package hu.riskguard.epr.registry.domain;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import hu.riskguard.epr.audit.AuditService;
import hu.riskguard.epr.audit.AuditSource;
import hu.riskguard.epr.audit.RegistryAuditEntry;
import hu.riskguard.epr.audit.events.FieldChangeEvent;
import hu.riskguard.epr.registry.internal.RegistryRepository;
import hu.riskguard.epr.registry.internal.RegistryRepository.RegistrySummary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    private final AuditService auditService;

    private final Cache<UUID, RegistrySummary> summaryCache =
            Caffeine.newBuilder()
                    .expireAfterWrite(10, TimeUnit.SECONDS)
                    .maximumSize(1000)
                    .build();

    public RegistryService(RegistryRepository registryRepository,
                           AuditService auditService) {
        this.registryRepository = registryRepository;
        this.auditService = auditService;
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

    // ─── Summary ──────────────────────────────────────────────────────────────

    public RegistrySummary getSummary(UUID tenantId) {
        return summaryCache.get(tenantId, registryRepository::countSummary);
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
                verifyMaterialTemplateBelongsToTenant(comp.materialTemplateId(), tenantId);
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
                verifyMaterialTemplateBelongsToTenant(compCmd.materialTemplateId(), tenantId);
                if (compCmd.id() != null && existingCompMap.containsKey(compCmd.id())) {
                    // Update existing component — diff-audit
                    incomingIds.add(compCmd.id());
                    ProductPackagingComponent oldComp = existingCompMap.get(compCmd.id());
                    diffComponentAndAudit(productId, tenantId, actingUserId, compCmd.id(), oldComp, compCmd);
                    registryRepository.updateComponent(compCmd.id(), tenantId, compCmd);
                } else {
                    // New component — honour classificationSource on the KF-code field so
                    // MANUAL_WIZARD / AI_SUGGESTED_* flows don't flatten to MANUAL on first
                    // insert (matches the update-path behaviour in diffComponentAndAudit).
                    UUID compId = registryRepository.insertComponent(productId, tenantId, compCmd);
                    AuditSource createSource = resolveClassificationSource(compCmd.classificationSource());
                    emitComponentCreateAudit(productId, tenantId, actingUserId, compId, compCmd, createSource);
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
        return auditService.listRegistryEntryAudit(productId, tenantId, page, size);
    }

    @Transactional(readOnly = true)
    public long countAuditLog(UUID tenantId, UUID productId) {
        return auditService.countRegistryEntryAudit(productId, tenantId);
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
        auditService.recordRegistryFieldChange(new FieldChangeEvent(
                productId, tenantId, field, oldVal, newVal, userId, source, null, null));
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

    /**
     * Parses {@code ComponentUpsertCommand.classificationSource()} to an {@link AuditSource},
     * silently falling back to {@code MANUAL} on null or unknown values — mirrors the
     * pattern in {@link #diffComponentAndAudit(UUID, UUID, UUID, UUID, ProductPackagingComponent, ComponentUpsertCommand)}.
     * Used by both the update-with-new-component path and (transitively) anywhere else that
     * needs to turn a String transit value into an enum.
     */
    private AuditSource resolveClassificationSource(String raw) {
        if (raw == null) return AuditSource.MANUAL;
        try {
            return AuditSource.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return AuditSource.MANUAL;
        }
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
        if (cmd.itemsPerParent() != null) {
            emitAudit(productId, tenantId, userId, prefix + "items_per_parent",
                    null, cmd.itemsPerParent().toPlainString(), source);
        }
        emitCreateAuditRaw(productId, tenantId, userId, prefix + "wrapping_level",
                Integer.toString(cmd.wrappingLevel()), source);
        if (cmd.materialTemplateId() != null) {
            emitCreateAuditRaw(productId, tenantId, userId, prefix + "material_template_id",
                    cmd.materialTemplateId().toString(), source);
        }
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
            auditService.recordRegistryFieldChange(new FieldChangeEvent(
                    productId, tenantId, prefix + "kf_code",
                    old.kfCode(), cmd.kfCode(), userId, kfSource,
                    kfStrategy, kfModelVersion));
        }

        // component_order changes must be audited (AC 5: per-field audit coverage)
        diffAndAudit(productId, tenantId, userId, prefix + "component_order",
                Integer.toString(old.componentOrder()),
                Integer.toString(cmd.componentOrder()));

        // items_per_parent (Story 10.1 — renamed+retyped from Story 9.6's units_per_product)
        diffBigDecimal(productId, tenantId, userId, prefix + "items_per_parent",
                old.itemsPerParent(), cmd.itemsPerParent());

        // wrapping_level (Story 10.1)
        diffAndAudit(productId, tenantId, userId, prefix + "wrapping_level",
                Integer.toString(old.wrappingLevel()),
                Integer.toString(cmd.wrappingLevel()));

        // material_template_id (Story 10.1 — nullable FK)
        diffAndAudit(productId, tenantId, userId, prefix + "material_template_id",
                old.materialTemplateId() != null ? old.materialTemplateId().toString() : null,
                cmd.materialTemplateId() != null ? cmd.materialTemplateId().toString() : null);

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

    /**
     * Verifies that a non-null {@code materialTemplateId} belongs to {@code tenantId}.
     * Throws 404 (not found) when the template does not exist or belongs to another tenant,
     * preventing cross-tenant template references (A-P1, code review 2026-04-18).
     */
    private void verifyMaterialTemplateBelongsToTenant(UUID materialTemplateId, UUID tenantId) {
        if (materialTemplateId == null) return;
        if (!registryRepository.existsMaterialTemplateForTenant(materialTemplateId, tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Material template not found: " + materialTemplateId);
        }
    }
}
