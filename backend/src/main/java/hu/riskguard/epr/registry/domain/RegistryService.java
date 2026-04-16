package hu.riskguard.epr.registry.domain;

import hu.riskguard.epr.registry.internal.RegistryAuditRepository;
import hu.riskguard.epr.registry.internal.RegistryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

/**
 * Domain service for the Product-Packaging Registry.
 *
 * <p>All writes are transactional and emit field-level audit rows per AC 5.
 * Diff-based auditing: only actually-changed fields produce audit rows (compare
 * {@code BigDecimal} with {@code compareTo}, not {@code equals}, to avoid false
 * positives on scale differences).
 *
 * <p>All mutations set {@code source = MANUAL} in this story (9.1). Stories 9.2/9.3
 * introduce the remaining {@link AuditSource} values.
 */
@Service
public class RegistryService {

    private final RegistryRepository registryRepository;
    private final RegistryAuditRepository auditRepository;

    public RegistryService(RegistryRepository registryRepository,
                           RegistryAuditRepository auditRepository) {
        this.registryRepository = registryRepository;
        this.auditRepository = auditRepository;
    }

    // ─── List ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProductSummary> list(UUID tenantId, RegistryListFilter filter, int page, int size) {
        return registryRepository.listByTenantWithFilters(tenantId, filter, page, size);
    }

    @Transactional(readOnly = true)
    public long count(UUID tenantId, RegistryListFilter filter) {
        return registryRepository.countByTenantWithFilters(tenantId, filter);
    }

    // ─── Get ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Product get(UUID tenantId, UUID productId) {
        var record = registryRepository.findProductByIdAndTenant(productId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product not found: " + productId));
        var components = registryRepository.findComponentsByProductAndTenant(productId, tenantId);
        return RegistryRepository.toProduct(record, components);
    }

    // ─── Create ──────────────────────────────────────────────────────────────

    /**
     * Create a new product with {@code AuditSource.MANUAL} (backward-compatible overload).
     */
    @Transactional
    public Product create(UUID tenantId, UUID actingUserId, ProductUpsertCommand cmd) {
        return create(tenantId, actingUserId, cmd, AuditSource.MANUAL);
    }

    /**
     * Create a new product, threading the given {@link AuditSource} into all audit rows.
     * Used by {@code RegistryBootstrapService} to record {@code NAV_BOOTSTRAP} as the source.
     */
    @Transactional
    public Product create(UUID tenantId, UUID actingUserId, ProductUpsertCommand cmd, AuditSource source) {
        UUID productId = registryRepository.insertProduct(tenantId, cmd);

        // Emit one audit row per populated product field
        emitCreateAudit(productId, tenantId, actingUserId, "article_number", cmd.articleNumber(), source);
        emitCreateAudit(productId, tenantId, actingUserId, "name", cmd.name(), source);
        emitCreateAudit(productId, tenantId, actingUserId, "vtsz", cmd.vtsz(), source);
        emitCreateAudit(productId, tenantId, actingUserId, "primary_unit", cmd.primaryUnit(), source);
        emitCreateAudit(productId, tenantId, actingUserId, "status",
                cmd.status() != null ? cmd.status().name() : null, source);

        // Insert components
        if (cmd.components() != null) {
            for (ComponentUpsertCommand comp : cmd.components()) {
                UUID compId = registryRepository.insertComponent(productId, tenantId, comp);
                emitComponentCreateAudit(productId, tenantId, actingUserId, compId, comp, source);
            }
        }

        return get(tenantId, productId);
    }

    // ─── Update ──────────────────────────────────────────────────────────────

    @Transactional
    public Product update(UUID tenantId, UUID productId, UUID actingUserId, ProductUpsertCommand cmd) {
        Product existing = get(tenantId, productId);

        // Diff product-level fields and track whether any product column changed
        boolean productChanged = false;
        productChanged |= diffAndAudit(productId, tenantId, actingUserId, "article_number",
                existing.articleNumber(), cmd.articleNumber());
        productChanged |= diffAndAudit(productId, tenantId, actingUserId, "name",
                existing.name(), cmd.name());
        productChanged |= diffAndAudit(productId, tenantId, actingUserId, "vtsz",
                existing.vtsz(), cmd.vtsz());
        productChanged |= diffAndAudit(productId, tenantId, actingUserId, "primary_unit",
                existing.primaryUnit(), cmd.primaryUnit());
        if (!Objects.equals(existing.status(), cmd.status())) {
            emitAudit(productId, tenantId, actingUserId, "status",
                    existing.status() != null ? existing.status().name() : null,
                    cmd.status() != null ? cmd.status().name() : null);
            productChanged = true;
        }

        if (productChanged) {
            int rows = registryRepository.updateProduct(productId, tenantId, cmd);
            if (rows == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product not found: " + productId);
            }
        }

        // Replace all components: delete existing, insert new (with diff auditing per component)
        Map<UUID, ProductPackagingComponent> existingCompMap = new LinkedHashMap<>();
        for (ProductPackagingComponent c : existing.components()) {
            existingCompMap.put(c.id(), c);
        }

        Set<UUID> incomingIds = new HashSet<>();
        if (cmd.components() != null) {
            for (ComponentUpsertCommand compCmd : cmd.components()) {
                if (compCmd.id() != null && existingCompMap.containsKey(compCmd.id())) {
                    // Update existing component — diff-audit
                    incomingIds.add(compCmd.id());
                    ProductPackagingComponent oldComp = existingCompMap.get(compCmd.id());
                    diffComponentAndAudit(productId, tenantId, actingUserId, compCmd.id(), oldComp, compCmd);
                    registryRepository.updateComponent(compCmd.id(), tenantId, compCmd);
                } else {
                    // New component
                    UUID compId = registryRepository.insertComponent(productId, tenantId, compCmd);
                    emitComponentCreateAudit(productId, tenantId, actingUserId, compId, compCmd);
                }
            }
        }

        // Remove components not present in incoming list — audit then delete in a single loop
        for (UUID existingId : existingCompMap.keySet()) {
            if (!incomingIds.contains(existingId)) {
                emitAudit(productId, tenantId, actingUserId,
                        "components[" + existingId + "].removed", "present", null);
                registryRepository.deleteComponentById(existingId, tenantId);
            }
        }

        return get(tenantId, productId);
    }

    // ─── Archive ─────────────────────────────────────────────────────────────

    @Transactional
    public void archive(UUID tenantId, UUID productId, UUID actingUserId) {
        Product existing = get(tenantId, productId);
        if (existing.status() == ProductStatus.ARCHIVED) {
            return; // idempotent
        }
        String oldStatus = existing.status() != null ? existing.status().name() : null;
        // Emit audit row BEFORE the DB write so a failure between the two leaves
        // the audit trail consistent with the DB state.
        emitAudit(productId, tenantId, actingUserId, "status",
                oldStatus, ProductStatus.ARCHIVED.name());
        int rows = registryRepository.archive(productId, tenantId);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Product not found: " + productId);
        }
    }

    // ─── Audit log ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RegistryAuditEntry> listAuditLog(UUID tenantId, UUID productId, int page, int size) {
        // Verify product belongs to tenant
        registryRepository.findProductByIdAndTenant(productId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product not found: " + productId));
        return auditRepository.listAuditByProduct(productId, tenantId, page, size);
    }

    @Transactional(readOnly = true)
    public long countAuditLog(UUID tenantId, UUID productId) {
        return auditRepository.countAuditByProduct(productId, tenantId);
    }

    // ─── Audit helpers ───────────────────────────────────────────────────────

    private void emitCreateAudit(UUID productId, UUID tenantId, UUID userId,
                                  String field, String value) {
        emitCreateAudit(productId, tenantId, userId, field, value, AuditSource.MANUAL);
    }

    private void emitCreateAudit(UUID productId, UUID tenantId, UUID userId,
                                  String field, String value, AuditSource source) {
        if (value == null || value.isBlank()) return;
        emitAudit(productId, tenantId, userId, "CREATE." + field, null, value, source);
    }

    private void emitAudit(UUID productId, UUID tenantId, UUID userId,
                            String field, String oldVal, String newVal) {
        emitAudit(productId, tenantId, userId, field, oldVal, newVal, AuditSource.MANUAL);
    }

    private void emitAudit(UUID productId, UUID tenantId, UUID userId,
                            String field, String oldVal, String newVal, AuditSource source) {
        auditRepository.insertAuditRow(productId, tenantId, field, oldVal, newVal, userId, source);
    }

    /** Returns true when an audit row was written (old != new). */
    private boolean diffAndAudit(UUID productId, UUID tenantId, UUID userId,
                                  String field, String oldVal, String newVal) {
        if (!Objects.equals(oldVal, newVal)) {
            emitAudit(productId, tenantId, userId, field, oldVal, newVal);
            return true;
        }
        return false;
    }

    private void emitComponentCreateAudit(UUID productId, UUID tenantId, UUID userId,
                                           UUID compId, ComponentUpsertCommand cmd) {
        emitComponentCreateAudit(productId, tenantId, userId, compId, cmd, AuditSource.MANUAL);
    }

    private void emitComponentCreateAudit(UUID productId, UUID tenantId, UUID userId,
                                           UUID compId, ComponentUpsertCommand cmd, AuditSource source) {
        // Normalised CREATE.components[<uuid>].<field> prefix so all create-time rows
        // share the same shape (AC 5, Story 9.3 AI-tagging relies on this).
        String prefix = "CREATE.components[" + compId + "].";
        emitCreateAuditRaw(productId, tenantId, userId, prefix + "material_description",
                cmd.materialDescription(), source);
        emitCreateAuditRaw(productId, tenantId, userId, prefix + "kf_code", cmd.kfCode(), source);
        if (cmd.weightPerUnitKg() != null) {
            emitAudit(productId, tenantId, userId, prefix + "weight_per_unit_kg",
                    null, cmd.weightPerUnitKg().toPlainString(), source);
        }
        emitCreateAuditRaw(productId, tenantId, userId, prefix + "component_order",
                Integer.toString(cmd.componentOrder()), source);
        emitCreateAuditRaw(productId, tenantId, userId, prefix + "units_per_product",
                Integer.toString(cmd.unitsPerProduct()), source);
        emitCreateAuditRaw(productId, tenantId, userId, prefix + "recyclability_grade",
                cmd.recyclabilityGrade() != null ? cmd.recyclabilityGrade().name() : null, source);
        if (cmd.recycledContentPct() != null) {
            emitAudit(productId, tenantId, userId, prefix + "recycled_content_pct",
                    null, cmd.recycledContentPct().toPlainString(), source);
        }
        if (cmd.reusable() != null) {
            emitAudit(productId, tenantId, userId, prefix + "reusable",
                    null, cmd.reusable().toString(), source);
        }
        if (cmd.substancesOfConcern() != null) {
            emitAudit(productId, tenantId, userId, prefix + "substances_of_concern",
                    null, cmd.substancesOfConcern().toString(), source);
        }
        emitCreateAuditRaw(productId, tenantId, userId, prefix + "supplier_declaration_ref",
                cmd.supplierDeclarationRef(), source);
    }

    /** Variant of emitCreateAudit that does NOT prepend "CREATE." — caller supplies full field. */
    private void emitCreateAuditRaw(UUID productId, UUID tenantId, UUID userId,
                                     String field, String value) {
        emitCreateAuditRaw(productId, tenantId, userId, field, value, AuditSource.MANUAL);
    }

    private void emitCreateAuditRaw(UUID productId, UUID tenantId, UUID userId,
                                     String field, String value, AuditSource source) {
        if (value == null || value.isBlank()) return;
        emitAudit(productId, tenantId, userId, field, null, value, source);
    }

    private void diffComponentAndAudit(UUID productId, UUID tenantId, UUID userId,
                                        UUID compId, ProductPackagingComponent old,
                                        ComponentUpsertCommand cmd) {
        String prefix = "components[" + compId + "].";
        diffAndAudit(productId, tenantId, userId, prefix + "material_description",
                old.materialDescription(), cmd.materialDescription());

        // kf_code: use classificationSource as AuditSource when provided (Story 9.3)
        if (!java.util.Objects.equals(old.kfCode(), cmd.kfCode())) {
            AuditSource kfSource = AuditSource.MANUAL;
            String kfStrategy = null;
            String kfModelVersion = null;
            if (cmd.classificationSource() != null) {
                try {
                    kfSource = AuditSource.valueOf(cmd.classificationSource());
                } catch (IllegalArgumentException ignored) {
                    // fall back to MANUAL for unknown values
                }
                if (kfSource == AuditSource.AI_SUGGESTED_CONFIRMED || kfSource == AuditSource.AI_SUGGESTED_EDITED) {
                    kfStrategy = cmd.classificationStrategy();
                    kfModelVersion = cmd.classificationModelVersion();
                }
            }
            auditRepository.insertAuditRow(productId, tenantId, prefix + "kf_code",
                    old.kfCode(), cmd.kfCode(), userId, kfSource, kfStrategy, kfModelVersion);
        }

        // component_order changes must be audited (AC 5: per-field audit coverage)
        diffAndAudit(productId, tenantId, userId, prefix + "component_order",
                Integer.toString(old.componentOrder()),
                Integer.toString(cmd.componentOrder()));

        // units_per_product (Story 9.6)
        diffAndAudit(productId, tenantId, userId, prefix + "units_per_product",
                Integer.toString(old.unitsPerProduct()),
                Integer.toString(cmd.unitsPerProduct()));

        // BigDecimal comparison via compareTo (avoids 0.70 vs 0.700 false positives)
        if (cmd.weightPerUnitKg() != null && old.weightPerUnitKg() != null
                && cmd.weightPerUnitKg().compareTo(old.weightPerUnitKg()) != 0) {
            emitAudit(productId, tenantId, userId, prefix + "weight_per_unit_kg",
                    old.weightPerUnitKg().toPlainString(), cmd.weightPerUnitKg().toPlainString());
        } else if (cmd.weightPerUnitKg() == null && old.weightPerUnitKg() != null) {
            emitAudit(productId, tenantId, userId, prefix + "weight_per_unit_kg",
                    old.weightPerUnitKg().toPlainString(), null);
        } else if (cmd.weightPerUnitKg() != null && old.weightPerUnitKg() == null) {
            emitAudit(productId, tenantId, userId, prefix + "weight_per_unit_kg",
                    null, cmd.weightPerUnitKg().toPlainString());
        }

        String oldGrade = old.recyclabilityGrade() != null ? old.recyclabilityGrade().name() : null;
        String newGrade = cmd.recyclabilityGrade() != null ? cmd.recyclabilityGrade().name() : null;
        diffAndAudit(productId, tenantId, userId, prefix + "recyclability_grade", oldGrade, newGrade);

        diffBigDecimal(productId, tenantId, userId, prefix + "recycled_content_pct",
                old.recycledContentPct(), cmd.recycledContentPct());

        if (!Objects.equals(old.reusable(), cmd.reusable())) {
            emitAudit(productId, tenantId, userId, prefix + "reusable",
                    old.reusable() != null ? old.reusable().toString() : null,
                    cmd.reusable() != null ? cmd.reusable().toString() : null);
        }

        // substances_of_concern diff — compare by serialised form (AC 5 via W13)
        String oldSubs = old.substancesOfConcern() != null ? old.substancesOfConcern().toString() : null;
        String newSubs = cmd.substancesOfConcern() != null ? cmd.substancesOfConcern().toString() : null;
        diffAndAudit(productId, tenantId, userId, prefix + "substances_of_concern",
                oldSubs, newSubs);

        diffAndAudit(productId, tenantId, userId, prefix + "supplier_declaration_ref",
                old.supplierDeclarationRef(), cmd.supplierDeclarationRef());
    }

    private void diffBigDecimal(UUID productId, UUID tenantId, UUID userId,
                                 String field, BigDecimal oldVal, BigDecimal newVal) {
        if (oldVal == null && newVal == null) return;
        if (oldVal != null && newVal != null && oldVal.compareTo(newVal) == 0) return;
        emitAudit(productId, tenantId, userId, field,
                oldVal != null ? oldVal.toPlainString() : null,
                newVal != null ? newVal.toPlainString() : null);
    }
}
