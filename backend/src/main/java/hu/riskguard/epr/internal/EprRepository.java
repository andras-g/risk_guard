package hu.riskguard.epr.internal;

import hu.riskguard.core.repository.BaseRepository;
import hu.riskguard.jooq.tables.records.EprMaterialTemplatesRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.JSONB;

import static hu.riskguard.jooq.Tables.EPR_CALCULATIONS;
import static hu.riskguard.jooq.Tables.EPR_CONFIGS;
import static hu.riskguard.jooq.Tables.EPR_MATERIAL_TEMPLATES;

/**
 * jOOQ repository for the EPR module.
 * Scoped to: {@code epr_configs}, {@code epr_calculations}, {@code epr_exports},
 * {@code epr_material_templates} tables ONLY.
 *
 * <p>Extends {@link BaseRepository} which provides {@code selectFromTenant()} and
 * {@code tenantCondition()} helpers for tenant-scoped queries.
 *
 * <p><b>Important — {@code updated_at} columns:</b> The {@code epr_material_templates} table
 * has an {@code updated_at} column with {@code DEFAULT now()} but no database trigger.
 * All UPDATE queries on this table MUST explicitly set
 * {@code .set(EPR_MATERIAL_TEMPLATES.UPDATED_AT, OffsetDateTime.now())} to keep the column
 * accurate.
 */
@Repository
public class EprRepository extends BaseRepository {

    public EprRepository(DSLContext dsl) {
        super(dsl);
    }

    /**
     * Insert a new material template.
     * Uses insertInto() with RETURNING to get the generated UUID without RecordListener dependency.
     *
     * @return the generated UUID of the new template
     */
    public UUID insertTemplate(UUID tenantId, String name, BigDecimal baseWeightGrams, boolean recurring) {
        return dsl.insertInto(EPR_MATERIAL_TEMPLATES)
                .set(EPR_MATERIAL_TEMPLATES.TENANT_ID, tenantId)
                .set(EPR_MATERIAL_TEMPLATES.NAME, name)
                .set(EPR_MATERIAL_TEMPLATES.BASE_WEIGHT_GRAMS, baseWeightGrams)
                .set(EPR_MATERIAL_TEMPLATES.RECURRING, recurring)
                .set(EPR_MATERIAL_TEMPLATES.VERIFIED, false)
                .returning(EPR_MATERIAL_TEMPLATES.ID)
                .fetchOne(EPR_MATERIAL_TEMPLATES.ID);
    }

    /**
     * Find all material templates for a tenant, ordered by created_at DESC.
     * Uses select().from() with explicit tenant filter (avoids TenantAwareDSLContext double-filter).
     */
    public List<EprMaterialTemplatesRecord> findAllByTenant(UUID tenantId) {
        return dsl.select(EPR_MATERIAL_TEMPLATES.asterisk())
                .from(EPR_MATERIAL_TEMPLATES)
                .where(EPR_MATERIAL_TEMPLATES.TENANT_ID.eq(tenantId))
                .orderBy(EPR_MATERIAL_TEMPLATES.CREATED_AT.desc())
                .fetchInto(EprMaterialTemplatesRecord.class);
    }

    /**
     * Find a single material template by ID and tenant (ownership verification).
     * Uses select().from() with explicit tenant filter (avoids TenantAwareDSLContext double-filter).
     */
    public Optional<EprMaterialTemplatesRecord> findByIdAndTenant(UUID id, UUID tenantId) {
        return dsl.select(EPR_MATERIAL_TEMPLATES.asterisk())
                .from(EPR_MATERIAL_TEMPLATES)
                .where(EPR_MATERIAL_TEMPLATES.ID.eq(id))
                .and(EPR_MATERIAL_TEMPLATES.TENANT_ID.eq(tenantId))
                .fetchOptionalInto(EprMaterialTemplatesRecord.class);
    }

    /**
     * Update a material template's name, base weight, and recurring flag.
     * MUST set updated_at explicitly — no DB trigger exists.
     *
     * @return true if exactly one row was updated
     */
    public boolean updateTemplate(UUID id, UUID tenantId, String name, BigDecimal baseWeightGrams, boolean recurring) {
        int rows = dsl.update(EPR_MATERIAL_TEMPLATES)
                .set(EPR_MATERIAL_TEMPLATES.NAME, name)
                .set(EPR_MATERIAL_TEMPLATES.BASE_WEIGHT_GRAMS, baseWeightGrams)
                .set(EPR_MATERIAL_TEMPLATES.RECURRING, recurring)
                .set(EPR_MATERIAL_TEMPLATES.UPDATED_AT, OffsetDateTime.now())
                .where(EPR_MATERIAL_TEMPLATES.ID.eq(id))
                .and(EPR_MATERIAL_TEMPLATES.TENANT_ID.eq(tenantId))
                .execute();
        return rows == 1;
    }

    /**
     * Delete a material template. ON DELETE SET NULL handles linked epr_calculations.
     *
     * @return true if exactly one row was deleted
     */
    public boolean deleteTemplate(UUID id, UUID tenantId) {
        int rows = dsl.deleteFrom(EPR_MATERIAL_TEMPLATES)
                .where(EPR_MATERIAL_TEMPLATES.ID.eq(id))
                .and(EPR_MATERIAL_TEMPLATES.TENANT_ID.eq(tenantId))
                .execute();
        return rows == 1;
    }

    /**
     * Update the recurring flag on a material template.
     * MUST set updated_at explicitly — no DB trigger exists.
     *
     * @return true if exactly one row was updated
     */
    public boolean updateRecurring(UUID id, UUID tenantId, boolean recurring) {
        int rows = dsl.update(EPR_MATERIAL_TEMPLATES)
                .set(EPR_MATERIAL_TEMPLATES.RECURRING, recurring)
                .set(EPR_MATERIAL_TEMPLATES.UPDATED_AT, OffsetDateTime.now())
                .where(EPR_MATERIAL_TEMPLATES.ID.eq(id))
                .and(EPR_MATERIAL_TEMPLATES.TENANT_ID.eq(tenantId))
                .execute();
        return rows == 1;
    }

    /**
     * Find templates for a tenant in a specific quarter.
     * Quarter is derived from created_at: Q1=Jan-Mar, Q2=Apr-Jun, Q3=Jul-Sep, Q4=Oct-Dec.
     */
    public List<EprMaterialTemplatesRecord> findByTenantAndQuarter(UUID tenantId, int year, int quarter) {
        OffsetDateTime start = quarterStart(year, quarter);
        OffsetDateTime end = quarterEnd(year, quarter);

        return dsl.select(EPR_MATERIAL_TEMPLATES.asterisk())
                .from(EPR_MATERIAL_TEMPLATES)
                .where(EPR_MATERIAL_TEMPLATES.TENANT_ID.eq(tenantId))
                .and(EPR_MATERIAL_TEMPLATES.CREATED_AT.ge(start))
                .and(EPR_MATERIAL_TEMPLATES.CREATED_AT.lt(end))
                .orderBy(EPR_MATERIAL_TEMPLATES.CREATED_AT.desc())
                .fetchInto(EprMaterialTemplatesRecord.class);
    }

    /**
     * Bulk insert copied templates. Each entry gets a new UUID and created_at=now(),
     * verified=false, kf_code=null.
     *
     * @param tenantId  the target tenant
     * @param templates list of (name, baseWeightGrams, recurring) tuples to copy
     * @return list of newly generated UUIDs
     */
    public List<UUID> bulkInsertTemplates(UUID tenantId, List<TemplateCopyData> templates) {
        return templates.stream()
                .map(t -> insertTemplate(tenantId, t.name(), t.baseWeightGrams(), t.recurring()))
                .toList();
    }

    /**
     * Data holder for copying a template — carries the mutable fields.
     */
    public record TemplateCopyData(String name, BigDecimal baseWeightGrams, boolean recurring) {}

    /**
     * Find multiple material templates by their IDs, scoped to a tenant.
     * Used to efficiently fetch only the newly copied templates (avoids N+1 or full-table scan).
     */
    public List<EprMaterialTemplatesRecord> findByIdsAndTenant(List<UUID> ids, UUID tenantId) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return dsl.select(EPR_MATERIAL_TEMPLATES.asterisk())
                .from(EPR_MATERIAL_TEMPLATES)
                .where(EPR_MATERIAL_TEMPLATES.ID.in(ids))
                .and(EPR_MATERIAL_TEMPLATES.TENANT_ID.eq(tenantId))
                .orderBy(EPR_MATERIAL_TEMPLATES.CREATED_AT.desc())
                .fetchInto(EprMaterialTemplatesRecord.class);
    }

    /**
     * Find all material templates for a tenant with override metadata from the latest linked calculation.
     * Uses a LEFT JOIN to fetch the most recent epr_calculations record per template.
     *
     * @return list of template records paired with override metadata (via {@link TemplateWithOverride})
     */
    public List<TemplateWithOverride> findAllByTenantWithOverride(UUID tenantId) {
        // Subquery: find the latest calculation per template
        var latestCalc = dsl.select(
                        EPR_CALCULATIONS.TEMPLATE_ID,
                        EPR_CALCULATIONS.KF_CODE.as("original_kf_code"),
                        EPR_CALCULATIONS.OVERRIDE_KF_CODE,
                        EPR_CALCULATIONS.OVERRIDE_REASON,
                        EPR_CALCULATIONS.CONFIDENCE,
                        EPR_CALCULATIONS.FEE_RATE,
                        org.jooq.impl.DSL.rowNumber().over(
                                org.jooq.impl.DSL.partitionBy(EPR_CALCULATIONS.TEMPLATE_ID)
                                        .orderBy(EPR_CALCULATIONS.CREATED_AT.desc())
                        ).as("rn"))
                .from(EPR_CALCULATIONS)
                .where(EPR_CALCULATIONS.TENANT_ID.eq(tenantId))
                .and(EPR_CALCULATIONS.TEMPLATE_ID.isNotNull())
                .asTable("latest_calc");

        return dsl.select(
                        EPR_MATERIAL_TEMPLATES.asterisk(),
                        latestCalc.field("override_kf_code", String.class),
                        latestCalc.field("override_reason", String.class),
                        latestCalc.field("confidence", String.class),
                        latestCalc.field("fee_rate", BigDecimal.class))
                .from(EPR_MATERIAL_TEMPLATES)
                .leftJoin(latestCalc)
                .on(latestCalc.field("template_id", UUID.class).eq(EPR_MATERIAL_TEMPLATES.ID)
                        .and(latestCalc.field("rn", Integer.class).eq(1)))
                .where(EPR_MATERIAL_TEMPLATES.TENANT_ID.eq(tenantId))
                .orderBy(EPR_MATERIAL_TEMPLATES.CREATED_AT.desc())
                .fetch(r -> new TemplateWithOverride(
                        r.into(EPR_MATERIAL_TEMPLATES).into(EprMaterialTemplatesRecord.class),
                        r.get("override_kf_code", String.class),
                        r.get("override_reason", String.class),
                        r.get("confidence", String.class),
                        r.get("fee_rate", BigDecimal.class)
                ));
    }

    /**
     * Pairs a material template record with optional override metadata from its latest calculation.
     */
    public record TemplateWithOverride(
            EprMaterialTemplatesRecord template,
            String overrideKfCode,
            String overrideReason,
            String confidence,
            BigDecimal feeRate
    ) {}

    // ─── Wizard / Config methods ────────────────────────────────────────────

    /**
     * Find the latest activated EPR config.
     * @return the active config record, or empty if none activated
     */
    public Optional<org.jooq.Record> findActiveConfig() {
        return dsl.select(EPR_CONFIGS.asterisk())
                .from(EPR_CONFIGS)
                .where(EPR_CONFIGS.ACTIVATED_AT.isNotNull())
                .orderBy(EPR_CONFIGS.VERSION.desc())
                .limit(1)
                .fetchOptional();
    }

    /**
     * Find a specific config by version number.
     * @return the config record, or empty if not found
     */
    public Optional<org.jooq.Record> findConfigByVersion(int version) {
        return dsl.select(EPR_CONFIGS.asterisk())
                .from(EPR_CONFIGS)
                .where(EPR_CONFIGS.VERSION.eq(version))
                .fetchOptional();
    }

    /**
     * Insert a new EPR calculation record with confidence and override fields.
     *
     * @return the generated calculation UUID
     */
    public UUID insertCalculation(UUID tenantId, int configVersion, JSONB traversalPath,
                                   String materialClassification, String kfCode,
                                   BigDecimal feeRate, UUID templateId,
                                   String confidence, String overrideKfCode, String overrideReason) {
        return dsl.insertInto(EPR_CALCULATIONS)
                .set(EPR_CALCULATIONS.TENANT_ID, tenantId)
                .set(EPR_CALCULATIONS.CONFIG_VERSION, configVersion)
                .set(EPR_CALCULATIONS.TRAVERSAL_PATH, traversalPath)
                .set(EPR_CALCULATIONS.MATERIAL_CLASSIFICATION, materialClassification)
                .set(EPR_CALCULATIONS.KF_CODE, kfCode)
                .set(EPR_CALCULATIONS.FEE_RATE, feeRate)
                .set(EPR_CALCULATIONS.TEMPLATE_ID, templateId)
                .set(EPR_CALCULATIONS.CONFIDENCE, confidence)
                .set(EPR_CALCULATIONS.OVERRIDE_KF_CODE, overrideKfCode)
                .set(EPR_CALCULATIONS.OVERRIDE_REASON, overrideReason)
                .returning(EPR_CALCULATIONS.ID)
                .fetchOne(EPR_CALCULATIONS.ID);
    }

    /**
     * Update a material template's KF code and mark as verified.
     * MUST set updated_at explicitly — no DB trigger exists.
     *
     * @return true if exactly one row was updated
     */
    public boolean updateTemplateKfCode(UUID templateId, UUID tenantId, String kfCode) {
        int rows = dsl.update(EPR_MATERIAL_TEMPLATES)
                .set(EPR_MATERIAL_TEMPLATES.KF_CODE, kfCode)
                .set(EPR_MATERIAL_TEMPLATES.VERIFIED, true)
                .set(EPR_MATERIAL_TEMPLATES.UPDATED_AT, OffsetDateTime.now())
                .where(EPR_MATERIAL_TEMPLATES.ID.eq(templateId))
                .and(EPR_MATERIAL_TEMPLATES.TENANT_ID.eq(tenantId))
                .execute();
        return rows == 1;
    }

    /**
     * Find a single calculation by ID and tenant (ownership verification for retry-link).
     *
     * @return the calculation record, or empty if not found or not owned by the tenant
     */
    public Optional<org.jooq.Record> findCalculationById(UUID calculationId, UUID tenantId) {
        return dsl.select(EPR_CALCULATIONS.asterisk())
                .from(EPR_CALCULATIONS)
                .where(EPR_CALCULATIONS.ID.eq(calculationId))
                .and(EPR_CALCULATIONS.TENANT_ID.eq(tenantId))
                .fetchOptional();
    }

    // ─── Quarter helpers ─────────────────────────────────────────────────────

    private static OffsetDateTime quarterStart(int year, int quarter) {
        int month = (quarter - 1) * 3 + 1;
        return OffsetDateTime.of(year, month, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
    }

    private static OffsetDateTime quarterEnd(int year, int quarter) {
        if (quarter == 4) {
            return OffsetDateTime.of(year + 1, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
        }
        int month = quarter * 3 + 1;
        return OffsetDateTime.of(year, month, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
    }
}
