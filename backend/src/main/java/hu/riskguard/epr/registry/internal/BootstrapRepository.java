package hu.riskguard.epr.registry.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.riskguard.core.repository.BaseRepository;
import hu.riskguard.epr.registry.domain.BootstrapCandidate;
import hu.riskguard.epr.registry.domain.BootstrapCandidateStatus;
import hu.riskguard.epr.registry.domain.BootstrapTriageFilter;
import hu.riskguard.epr.registry.classifier.ClassificationResult;
import hu.riskguard.epr.registry.classifier.KfSuggestion;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.REGISTRY_BOOTSTRAP_CANDIDATES;

/**
 * jOOQ repository for the {@code registry_bootstrap_candidates} table.
 * All reads use {@link #tenantCondition} for tenant isolation.
 */
@Repository
public class BootstrapRepository extends BaseRepository {

    private static final Logger log = LoggerFactory.getLogger(BootstrapRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public BootstrapRepository(DSLContext dsl) {
        super(dsl);
    }

    // ─── Writes ──────────────────────────────────────────────────────────────

    /**
     * Insert a new bootstrap candidate row using ON CONFLICT DO NOTHING to handle concurrent
     * trigger calls that race past the existsByTenantAndDedupeKey check (P1 fix).
     *
     * @return {@code true} if a new row was inserted, {@code false} if it already existed
     */
    public boolean insertCandidateIfNew(UUID tenantId, String productName, String vtsz,
                                        int frequency, BigDecimal totalQuantity, String unitOfMeasure,
                                        ClassificationResult classification) {
        // Primary-layer KF code for backward-compatible display in triage table
        String suggestedKfCode = classification.suggestions().stream()
                .filter(s -> "primary".equals(s.layer()))
                .findFirst()
                .map(KfSuggestion::kfCode)
                .orElse(classification.suggestions().isEmpty() ? null : classification.suggestions().get(0).kfCode());
        String suggestedComponents = serializeSuggestions(classification.suggestions());
        String strategyName = classification.strategy() != null
                ? classification.strategy().name()
                : null;
        String confidenceName = classification.confidence() != null
                ? classification.confidence().name()
                : null;

        int count = dsl.insertInto(REGISTRY_BOOTSTRAP_CANDIDATES)
                .set(REGISTRY_BOOTSTRAP_CANDIDATES.TENANT_ID, tenantId)
                .set(REGISTRY_BOOTSTRAP_CANDIDATES.PRODUCT_NAME, productName)
                .set(REGISTRY_BOOTSTRAP_CANDIDATES.VTSZ, vtsz)
                .set(REGISTRY_BOOTSTRAP_CANDIDATES.FREQUENCY, frequency)
                .set(REGISTRY_BOOTSTRAP_CANDIDATES.TOTAL_QUANTITY, totalQuantity)
                .set(REGISTRY_BOOTSTRAP_CANDIDATES.UNIT_OF_MEASURE, unitOfMeasure)
                .set(REGISTRY_BOOTSTRAP_CANDIDATES.STATUS, BootstrapCandidateStatus.PENDING.name())
                .set(REGISTRY_BOOTSTRAP_CANDIDATES.SUGGESTED_KF_CODE, suggestedKfCode)
                .set(REGISTRY_BOOTSTRAP_CANDIDATES.SUGGESTED_COMPONENTS,
                        suggestedComponents != null ? JSONB.valueOf(suggestedComponents) : null)
                .set(REGISTRY_BOOTSTRAP_CANDIDATES.CLASSIFICATION_STRATEGY, strategyName)
                .set(REGISTRY_BOOTSTRAP_CANDIDATES.CLASSIFICATION_CONFIDENCE, confidenceName)
                .onConflictDoNothing()
                .execute();

        return count > 0;
    }

    /**
     * Update the status of a candidate, but only if its current status matches
     * {@code requiredCurrentStatus} — prevents concurrent approve/reject races (P2 fix).
     *
     * @return number of affected rows (0 if not found, wrong tenant, or status already changed)
     */
    public int updateCandidateStatus(UUID tenantId, UUID candidateId,
                                      BootstrapCandidateStatus newStatus, UUID resultingProductId,
                                      BootstrapCandidateStatus requiredCurrentStatus) {
        var update = dsl.update(REGISTRY_BOOTSTRAP_CANDIDATES)
                .set(REGISTRY_BOOTSTRAP_CANDIDATES.STATUS, newStatus.name())
                .set(REGISTRY_BOOTSTRAP_CANDIDATES.UPDATED_AT, OffsetDateTime.now());

        if (resultingProductId != null) {
            update = update.set(REGISTRY_BOOTSTRAP_CANDIDATES.RESULTING_PRODUCT_ID, resultingProductId);
        }

        return update
                .where(REGISTRY_BOOTSTRAP_CANDIDATES.ID.eq(candidateId))
                .and(tenantCondition(REGISTRY_BOOTSTRAP_CANDIDATES.TENANT_ID))
                .and(REGISTRY_BOOTSTRAP_CANDIDATES.STATUS.eq(requiredCurrentStatus.name()))
                .execute();
    }

    // ─── Reads ───────────────────────────────────────────────────────────────

    /**
     * Find a single candidate by ID, ensuring it belongs to the current tenant.
     */
    public Optional<BootstrapCandidate> findByIdAndTenant(UUID candidateId, UUID tenantId) {
        return dsl.selectFrom(REGISTRY_BOOTSTRAP_CANDIDATES)
                .where(REGISTRY_BOOTSTRAP_CANDIDATES.ID.eq(candidateId))
                .and(tenantCondition(REGISTRY_BOOTSTRAP_CANDIDATES.TENANT_ID))
                .fetchOptional(this::toCandidate);
    }

    /**
     * Check whether a candidate with the same dedup key already exists for this tenant.
     * Uses the normalized (productName, vtsz) pair — any status counts.
     */
    public boolean existsByTenantAndDedupeKey(UUID tenantId, String productName, String vtsz) {
        String normalizedName = normalize(productName);
        String normalizedVtsz = normalize(vtsz);

        return dsl.fetchExists(
                dsl.selectOne()
                        .from(REGISTRY_BOOTSTRAP_CANDIDATES)
                        .where(REGISTRY_BOOTSTRAP_CANDIDATES.TENANT_ID.eq(tenantId))
                        .and(REGISTRY_BOOTSTRAP_CANDIDATES.PRODUCT_NAME.eq(normalizedName))
                        .and(normalizedVtsz.isEmpty()
                                ? REGISTRY_BOOTSTRAP_CANDIDATES.VTSZ.isNull()
                                : REGISTRY_BOOTSTRAP_CANDIDATES.VTSZ.eq(normalizedVtsz))
        );
    }

    /**
     * Paginated list of candidates, optionally filtered by status.
     * Default sort: frequency DESC, total_quantity DESC, product_name ASC.
     */
    public List<BootstrapCandidate> listByTenantWithFilter(UUID tenantId,
                                                             BootstrapTriageFilter filter,
                                                             int page, int size) {
        Condition condition = REGISTRY_BOOTSTRAP_CANDIDATES.TENANT_ID.eq(tenantId);
        if (filter != null && filter.status() != null) {
            condition = condition.and(
                    REGISTRY_BOOTSTRAP_CANDIDATES.STATUS.eq(filter.status().name()));
        }
        return dsl.selectFrom(REGISTRY_BOOTSTRAP_CANDIDATES)
                .where(condition)
                .orderBy(
                        REGISTRY_BOOTSTRAP_CANDIDATES.FREQUENCY.desc(),
                        REGISTRY_BOOTSTRAP_CANDIDATES.TOTAL_QUANTITY.desc(),
                        REGISTRY_BOOTSTRAP_CANDIDATES.PRODUCT_NAME.asc()
                )
                .limit(size)
                .offset((long) page * size)
                .fetch(this::toCandidate);
    }

    /**
     * Count candidates for a tenant, optionally filtered by status.
     */
    public long countByTenantWithFilter(UUID tenantId, BootstrapTriageFilter filter) {
        Condition condition = REGISTRY_BOOTSTRAP_CANDIDATES.TENANT_ID.eq(tenantId);
        if (filter != null && filter.status() != null) {
            condition = condition.and(
                    REGISTRY_BOOTSTRAP_CANDIDATES.STATUS.eq(filter.status().name()));
        }
        Long count = dsl.selectCount()
                .from(REGISTRY_BOOTSTRAP_CANDIDATES)
                .where(condition)
                .fetchOne(0, Long.class);
        return count != null ? count : 0L;
    }

    // ─── Serialization ─────────────────────────────────────────────────────────

    private static String serializeSuggestions(List<KfSuggestion> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) return null;
        try {
            var list = suggestions.stream().map(s -> {
                var node = MAPPER.createObjectNode();
                node.put("layer", s.layer());
                node.put("kfCode", s.kfCode());
                node.put("description", s.description());
                if (s.weightEstimateKg() != null) {
                    node.put("weightEstimateKg", s.weightEstimateKg());
                }
                node.put("unitsPerProduct", s.unitsPerProduct());
                node.put("score", s.score());
                return node;
            }).toList();
            return MAPPER.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize suggested_components: {}", e.getMessage());
            return null;
        }
    }

    // ─── Normalization (mirrors RegistryBootstrapService.normalize) ───────────

    public static String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase().replaceAll("\\s+", " ");
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private BootstrapCandidate toCandidate(
            org.jooq.Record r) {
        var rec = r.into(REGISTRY_BOOTSTRAP_CANDIDATES);
        String statusStr = rec.getStatus();
        BootstrapCandidateStatus status = statusStr != null
                ? BootstrapCandidateStatus.valueOf(statusStr)
                : BootstrapCandidateStatus.PENDING;

        String suggestedComponents = rec.getSuggestedComponents() != null
                ? rec.getSuggestedComponents().data()
                : null;

        return new BootstrapCandidate(
                rec.getId(),
                rec.getTenantId(),
                rec.getProductName(),
                rec.getVtsz(),
                rec.getFrequency() != null ? rec.getFrequency() : 1,
                rec.getTotalQuantity(),
                rec.getUnitOfMeasure(),
                status,
                rec.getSuggestedKfCode(),
                suggestedComponents,
                rec.getClassificationStrategy(),
                rec.getClassificationConfidence(),
                rec.getResultingProductId(),
                rec.getCreatedAt(),
                rec.getUpdatedAt()
        );
    }
}
