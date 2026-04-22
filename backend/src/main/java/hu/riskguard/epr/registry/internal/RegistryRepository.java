package hu.riskguard.epr.registry.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.riskguard.core.repository.BaseRepository;
import hu.riskguard.epr.audit.AuditSource;
import hu.riskguard.epr.registry.domain.*;
import hu.riskguard.jooq.tables.records.ProductPackagingComponentsRecord;
import hu.riskguard.jooq.tables.records.ProductsRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static hu.riskguard.jooq.Tables.EPR_MATERIAL_TEMPLATES;
import static hu.riskguard.jooq.Tables.PRODUCT_PACKAGING_COMPONENTS;
import static hu.riskguard.jooq.Tables.PRODUCTS;
import static org.jooq.impl.DSL.max;

/**
 * jOOQ repository for the EPR registry module.
 * Owns {@code products} and {@code product_packaging_components} tables.
 *
 * <p>Tenant isolation for {@code product_packaging_components}:
 * The table has NO {@code tenant_id} column. Every read/write MUST join through
 * {@code products} and filter on {@code tenant_id} — never load components by
 * {@code product_id} alone without the tenant join.
 */
@Repository
public class RegistryRepository extends BaseRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public RegistryRepository(DSLContext dsl) {
        super(dsl);
    }

    // ─── Product writes ──────────────────────────────────────────────────────

    public UUID insertProduct(UUID tenantId, ProductUpsertCommand cmd) {
        return insertProduct(tenantId, cmd, null);
    }

    /**
     * Insert a product, optionally stamping {@code epr_scope} to the given value. When
     * {@code resolvedEprScope} is null the DB DEFAULT ({@code 'UNKNOWN'}) takes effect — kept as
     * null to preserve current behaviour for callers that have not yet migrated to Story 10.11.
     */
    public UUID insertProduct(UUID tenantId, ProductUpsertCommand cmd, String resolvedEprScope) {
        var insert = dsl.insertInto(PRODUCTS)
                .set(PRODUCTS.TENANT_ID, tenantId)
                .set(PRODUCTS.ARTICLE_NUMBER, cmd.articleNumber())
                .set(PRODUCTS.NAME, cmd.name())
                .set(PRODUCTS.VTSZ, cmd.vtsz())
                .set(PRODUCTS.PRIMARY_UNIT, cmd.primaryUnit())
                .set(PRODUCTS.STATUS, cmd.status().name());
        if (resolvedEprScope != null) {
            insert = insert.set(PRODUCTS.EPR_SCOPE, resolvedEprScope);
        }
        UUID id = insert
                .returning(PRODUCTS.ID)
                .fetchOne(PRODUCTS.ID);
        if (id == null) {
            throw new IllegalStateException("Failed to insert product — no ID returned");
        }
        return id;
    }

    public int updateProduct(UUID productId, UUID tenantId, ProductUpsertCommand cmd) {
        return dsl.update(PRODUCTS)
                .set(PRODUCTS.ARTICLE_NUMBER, cmd.articleNumber())
                .set(PRODUCTS.NAME, cmd.name())
                .set(PRODUCTS.VTSZ, cmd.vtsz())
                .set(PRODUCTS.PRIMARY_UNIT, cmd.primaryUnit())
                .set(PRODUCTS.STATUS, cmd.status().name())
                .set(PRODUCTS.UPDATED_AT, OffsetDateTime.now())
                .where(PRODUCTS.ID.eq(productId))
                .and(PRODUCTS.TENANT_ID.eq(tenantId))
                .execute();
    }

    public int archive(UUID productId, UUID tenantId) {
        return dsl.update(PRODUCTS)
                .set(PRODUCTS.STATUS, ProductStatus.ARCHIVED.name())
                .set(PRODUCTS.UPDATED_AT, OffsetDateTime.now())
                .where(PRODUCTS.ID.eq(productId))
                .and(PRODUCTS.TENANT_ID.eq(tenantId))
                .execute();
    }

    // ─── Product reads ───────────────────────────────────────────────────────

    public Optional<ProductsRecord> findProductByIdAndTenant(UUID productId, UUID tenantId) {
        return dsl.select(PRODUCTS.asterisk())
                .from(PRODUCTS)
                .where(PRODUCTS.ID.eq(productId))
                .and(PRODUCTS.TENANT_ID.eq(tenantId))
                .fetchOptionalInto(ProductsRecord.class);
    }

    /**
     * Load all components for a product, verifying tenant ownership via a join.
     * Components are ordered by {@code component_order ASC}.
     */
    public List<ProductPackagingComponentsRecord> findComponentsByProductAndTenant(UUID productId, UUID tenantId) {
        return dsl.select(PRODUCT_PACKAGING_COMPONENTS.asterisk())
                .from(PRODUCT_PACKAGING_COMPONENTS)
                .join(PRODUCTS).on(PRODUCTS.ID.eq(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID))
                .where(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID.eq(productId))
                .and(PRODUCTS.TENANT_ID.eq(tenantId))
                .orderBy(PRODUCT_PACKAGING_COMPONENTS.COMPONENT_ORDER.asc())
                .fetchInto(ProductPackagingComponentsRecord.class);
    }

    /**
     * List products with filters, returning a page of summaries.
     * Component count is computed via a correlated subquery.
     */
    public List<ProductSummary> listByTenantWithFilters(UUID tenantId, RegistryListFilter filter,
                                                         int page, int size) {
        Condition condition = PRODUCTS.TENANT_ID.eq(tenantId);
        condition = applyFilters(condition, filter, tenantId);

        var countSub = DSL.select(DSL.count())
                .from(PRODUCT_PACKAGING_COMPONENTS)
                .where(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID.eq(PRODUCTS.ID))
                .asField("component_count");

        var vtszFallbackBadge = DSL.field(
                DSL.when(DSL.exists(
                        DSL.selectOne()
                                .from(PRODUCT_PACKAGING_COMPONENTS)
                                .where(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID.eq(PRODUCTS.ID))
                                .and(PRODUCT_PACKAGING_COMPONENTS.CLASSIFIER_SOURCE.eq("VTSZ_FALLBACK"))
                ), DSL.inline("VTSZ_FALLBACK"))
                .otherwise(DSL.inline((String) null))
        ).as("classifier_source_badge");

        return dsl.select(
                        PRODUCTS.ID,
                        PRODUCTS.TENANT_ID,
                        PRODUCTS.ARTICLE_NUMBER,
                        PRODUCTS.NAME,
                        PRODUCTS.VTSZ,
                        PRODUCTS.PRIMARY_UNIT,
                        PRODUCTS.STATUS,
                        PRODUCTS.REVIEW_STATE,
                        PRODUCTS.EPR_SCOPE,
                        vtszFallbackBadge,
                        countSub,
                        PRODUCTS.CREATED_AT,
                        PRODUCTS.UPDATED_AT
                )
                .from(PRODUCTS)
                .where(condition)
                .orderBy(PRODUCTS.UPDATED_AT.desc())
                .limit(size)
                .offset((long) page * size)
                .fetch(r -> new ProductSummary(
                        r.get(PRODUCTS.ID),
                        r.get(PRODUCTS.TENANT_ID),
                        r.get(PRODUCTS.ARTICLE_NUMBER),
                        r.get(PRODUCTS.NAME),
                        r.get(PRODUCTS.VTSZ),
                        r.get(PRODUCTS.PRIMARY_UNIT),
                        ProductStatus.valueOf(r.get(PRODUCTS.STATUS)),
                        r.get(PRODUCTS.REVIEW_STATE) != null
                                ? ReviewState.valueOf(r.get(PRODUCTS.REVIEW_STATE)) : null,
                        r.get("classifier_source_badge", String.class),
                        r.get(PRODUCTS.EPR_SCOPE),
                        r.get("component_count", Integer.class),
                        r.get(PRODUCTS.CREATED_AT),
                        r.get(PRODUCTS.UPDATED_AT)
                ));
    }

    public long countByTenantWithFilters(UUID tenantId, RegistryListFilter filter) {
        Condition condition = PRODUCTS.TENANT_ID.eq(tenantId);
        condition = applyFilters(condition, filter, tenantId);

        Long result = dsl.selectCount()
                .from(PRODUCTS)
                .where(condition)
                .fetchOne(0, Long.class);
        return result == null ? 0L : result;
    }

    private Condition applyFilters(Condition condition, RegistryListFilter filter, UUID tenantId) {
        if (filter == null) return condition;

        if (filter.q() != null && !filter.q().isBlank()) {
            // Escape LIKE wildcards in user input; '!' is the escape char
            String safe = filter.q()
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");
            condition = condition.and(PRODUCTS.NAME.likeIgnoreCase("%" + safe + "%", '!'));
        }
        if (filter.vtsz() != null && !filter.vtsz().isBlank()) {
            condition = condition.and(PRODUCTS.VTSZ.startsWith(filter.vtsz()));
        }
        if (filter.status() != null) {
            condition = condition.and(PRODUCTS.STATUS.eq(filter.status().name()));
        }
        if (filter.kfCode() != null && !filter.kfCode().isBlank()) {
            // Tenant scoping is enforced by the outer PRODUCTS.TENANT_ID = tenantId predicate;
            // the correlated subquery only needs the product_id correlation.
            condition = condition.and(DSL.exists(
                    DSL.selectOne()
                            .from(PRODUCT_PACKAGING_COMPONENTS)
                            .where(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID.eq(PRODUCTS.ID))
                            .and(PRODUCT_PACKAGING_COMPONENTS.KF_CODE.eq(filter.kfCode()))
            ));
        }
        // Story 10.4: reviewState filter ("Csak hiányos" chip)
        if (filter.reviewState() != null) {
            condition = condition.and(PRODUCTS.REVIEW_STATE.eq(filter.reviewState().name()));
        }
        // Story 10.4: classifierSource filter ("Csak bizonytalan" chip)
        if (filter.classifierSource() != null) {
            AuditSource src = filter.classifierSource();
            condition = condition.and(DSL.exists(
                    DSL.selectOne()
                            .from(PRODUCT_PACKAGING_COMPONENTS)
                            .where(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID.eq(PRODUCTS.ID))
                            .and(PRODUCT_PACKAGING_COMPONENTS.CLASSIFIER_SOURCE.eq(src.name()))
            ));
        }
        // Story 10.11 AC #18: onlyUnknownScope filter chip
        if (Boolean.TRUE.equals(filter.onlyUnknownScope())) {
            condition = condition.and(PRODUCTS.EPR_SCOPE.eq("UNKNOWN"));
        }
        return condition;
    }

    // ─── Component writes ────────────────────────────────────────────────────

    public UUID insertComponent(UUID productId, UUID tenantId, ComponentUpsertCommand cmd) {
        // Defence-in-depth: verify the target product belongs to the caller's tenant
        // before inserting a child component. Service layer also checks this, but the
        // repository contract must be safe in isolation.
        boolean productOwnedByTenant = dsl.fetchExists(
                DSL.selectOne().from(PRODUCTS)
                        .where(PRODUCTS.ID.eq(productId))
                        .and(PRODUCTS.TENANT_ID.eq(tenantId))
        );
        if (!productOwnedByTenant) {
            throw new IllegalStateException(
                    "Refusing to insert component: product " + productId + " is not owned by tenant " + tenantId);
        }
        UUID id = dsl.insertInto(PRODUCT_PACKAGING_COMPONENTS)
                .set(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID, productId)
                .set(PRODUCT_PACKAGING_COMPONENTS.MATERIAL_DESCRIPTION, cmd.materialDescription())
                .set(PRODUCT_PACKAGING_COMPONENTS.KF_CODE, cmd.kfCode())
                .set(PRODUCT_PACKAGING_COMPONENTS.WEIGHT_PER_UNIT_KG, cmd.weightPerUnitKg())
                .set(PRODUCT_PACKAGING_COMPONENTS.COMPONENT_ORDER, cmd.componentOrder())
                .set(PRODUCT_PACKAGING_COMPONENTS.ITEMS_PER_PARENT, cmd.itemsPerParent())
                .set(PRODUCT_PACKAGING_COMPONENTS.WRAPPING_LEVEL, cmd.wrappingLevel())
                .set(PRODUCT_PACKAGING_COMPONENTS.MATERIAL_TEMPLATE_ID, cmd.materialTemplateId())
                .set(PRODUCT_PACKAGING_COMPONENTS.RECYCLABILITY_GRADE,
                        cmd.recyclabilityGrade() != null ? cmd.recyclabilityGrade().name() : null)
                .set(PRODUCT_PACKAGING_COMPONENTS.RECYCLED_CONTENT_PCT, cmd.recycledContentPct())
                .set(PRODUCT_PACKAGING_COMPONENTS.REUSABLE, cmd.reusable())
                .set(PRODUCT_PACKAGING_COMPONENTS.SUBSTANCES_OF_CONCERN,
                        toJsonb(cmd.substancesOfConcern()))
                .set(PRODUCT_PACKAGING_COMPONENTS.SUPPLIER_DECLARATION_REF, cmd.supplierDeclarationRef())
                .set(PRODUCT_PACKAGING_COMPONENTS.CLASSIFIER_SOURCE, cmd.classificationSource())
                .returning(PRODUCT_PACKAGING_COMPONENTS.ID)
                .fetchOne(PRODUCT_PACKAGING_COMPONENTS.ID);
        if (id == null) {
            throw new IllegalStateException("Failed to insert component — no ID returned");
        }
        return id;
    }

    public void updateComponent(UUID componentId, UUID tenantId, ComponentUpsertCommand cmd) {
        // Update via join to guarantee tenant ownership
        dsl.update(PRODUCT_PACKAGING_COMPONENTS)
                .set(PRODUCT_PACKAGING_COMPONENTS.MATERIAL_DESCRIPTION, cmd.materialDescription())
                .set(PRODUCT_PACKAGING_COMPONENTS.KF_CODE, cmd.kfCode())
                .set(PRODUCT_PACKAGING_COMPONENTS.WEIGHT_PER_UNIT_KG, cmd.weightPerUnitKg())
                .set(PRODUCT_PACKAGING_COMPONENTS.COMPONENT_ORDER, cmd.componentOrder())
                .set(PRODUCT_PACKAGING_COMPONENTS.ITEMS_PER_PARENT, cmd.itemsPerParent())
                .set(PRODUCT_PACKAGING_COMPONENTS.WRAPPING_LEVEL, cmd.wrappingLevel())
                .set(PRODUCT_PACKAGING_COMPONENTS.MATERIAL_TEMPLATE_ID, cmd.materialTemplateId())
                .set(PRODUCT_PACKAGING_COMPONENTS.RECYCLABILITY_GRADE,
                        cmd.recyclabilityGrade() != null ? cmd.recyclabilityGrade().name() : null)
                .set(PRODUCT_PACKAGING_COMPONENTS.RECYCLED_CONTENT_PCT, cmd.recycledContentPct())
                .set(PRODUCT_PACKAGING_COMPONENTS.REUSABLE, cmd.reusable())
                .set(PRODUCT_PACKAGING_COMPONENTS.SUBSTANCES_OF_CONCERN,
                        toJsonb(cmd.substancesOfConcern()))
                .set(PRODUCT_PACKAGING_COMPONENTS.SUPPLIER_DECLARATION_REF, cmd.supplierDeclarationRef())
                .set(PRODUCT_PACKAGING_COMPONENTS.UPDATED_AT, OffsetDateTime.now())
                .where(PRODUCT_PACKAGING_COMPONENTS.ID.eq(componentId))
                .and(DSL.exists(
                        DSL.selectOne().from(PRODUCTS)
                                .where(PRODUCTS.ID.eq(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID))
                                .and(PRODUCTS.TENANT_ID.eq(tenantId))
                ))
                .execute();
    }

    public void deleteComponentsForProduct(UUID productId, UUID tenantId) {
        // Validate tenant owns the product before cascading delete
        dsl.deleteFrom(PRODUCT_PACKAGING_COMPONENTS)
                .where(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID.eq(productId))
                .and(DSL.exists(
                        DSL.selectOne().from(PRODUCTS)
                                .where(PRODUCTS.ID.eq(productId))
                                .and(PRODUCTS.TENANT_ID.eq(tenantId))
                ))
                .execute();
    }

    public void deleteComponentById(UUID componentId, UUID tenantId) {
        dsl.deleteFrom(PRODUCT_PACKAGING_COMPONENTS)
                .where(PRODUCT_PACKAGING_COMPONENTS.ID.eq(componentId))
                .and(DSL.exists(
                        DSL.selectOne().from(PRODUCTS)
                                .where(PRODUCTS.ID.eq(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID))
                                .and(PRODUCTS.TENANT_ID.eq(tenantId))
                ))
                .execute();
    }

    /**
     * Find ACTIVE products by exact VTSZ match for a tenant, ordered by updated_at ASC (oldest first on tie).
     * Used by RegistryLookupService for VTSZ-based product matching.
     *
     * <p><b>Tenant isolation:</b> always scoped to {@code tenantId} — NEVER pass null here.
     */
    public List<ProductsRecord> findActiveByVtsz(UUID tenantId, String vtsz) {
        return dsl.select(PRODUCTS.asterisk())
                .from(PRODUCTS)
                .where(PRODUCTS.TENANT_ID.eq(tenantId))
                .and(PRODUCTS.VTSZ.eq(vtsz))
                .and(PRODUCTS.STATUS.eq(ProductStatus.ACTIVE.name()))
                .orderBy(PRODUCTS.UPDATED_AT.asc())
                .fetchInto(ProductsRecord.class);
    }

    /**
     * Find ACTIVE products by exact article number for a tenant.
     * Used by RegistryLookupService for article-number-based product matching.
     *
     * <p><b>Tenant isolation:</b> always scoped to {@code tenantId} — NEVER pass null here.
     */
    public Optional<ProductsRecord> findActiveByArticleNumber(UUID tenantId, String articleNumber) {
        return dsl.select(PRODUCTS.asterisk())
                .from(PRODUCTS)
                .where(PRODUCTS.TENANT_ID.eq(tenantId))
                .and(PRODUCTS.ARTICLE_NUMBER.eq(articleNumber))
                .and(PRODUCTS.STATUS.eq(ProductStatus.ACTIVE.name()))
                .fetchOptionalInto(ProductsRecord.class);
    }

    // ─── Material template helpers ───────────────────────────────────────────

    /**
     * Returns {@code true} when the given material-template row exists AND belongs to {@code tenantId}.
     * Used by RegistryService to reject cross-tenant {@code materialTemplateId} values before insert/update.
     */
    public boolean existsMaterialTemplateForTenant(UUID templateId, UUID tenantId) {
        return dsl.fetchExists(
                DSL.selectOne().from(EPR_MATERIAL_TEMPLATES)
                        .where(EPR_MATERIAL_TEMPLATES.ID.eq(templateId))
                        .and(EPR_MATERIAL_TEMPLATES.TENANT_ID.eq(tenantId))
        );
    }

    // ─── Aggregation reads ────────────────────────────────────────────────────

    /**
     * One row per PRODUCTS LEFT JOIN PRODUCT_PACKAGING_COMPONENTS.
     * {@code componentId} is null when the product has zero components.
     *
     * <p>Story 10.11: {@code eprScope} surfaces the per-product scope flag so downstream aggregators
     * can distinguish FIRST_PLACER vs UNKNOWN rows (both pass the RESELLER-excluding filter, but
     * UNKNOWN rows contribute to the unclassified-count warning counter).
     */
    public record AggregationRow(
            UUID productId, String vtsz, String name, String reviewState, String eprScope,
            UUID componentId, String kfCode, Integer wrappingLevel,
            BigDecimal itemsPerParent, BigDecimal weightPerUnitKg,
            String classifierSource, Integer componentOrder, String materialDescription
    ) {}

    /** Bulk-load active registry for the aggregator — one LEFT JOIN query per tenant.
     *
     * <p>Story 10.11 AC #3: filters on {@code epr_scope IN ('FIRST_PLACER','UNKNOWN')} — RESELLER
     * rows are dropped (out-of-scope for filing). UNKNOWN is INCLUDED as the compliance-safe default
     * per product-decision locked 2026-04-22.
     */
    public List<AggregationRow> loadForAggregation(UUID tenantId) {
        var compId = PRODUCT_PACKAGING_COMPONENTS.ID.as("comp_id");
        return dsl.select(
                        PRODUCTS.ID, PRODUCTS.VTSZ, PRODUCTS.NAME, PRODUCTS.REVIEW_STATE, PRODUCTS.EPR_SCOPE,
                        compId,
                        PRODUCT_PACKAGING_COMPONENTS.KF_CODE,
                        PRODUCT_PACKAGING_COMPONENTS.WRAPPING_LEVEL,
                        PRODUCT_PACKAGING_COMPONENTS.ITEMS_PER_PARENT,
                        PRODUCT_PACKAGING_COMPONENTS.WEIGHT_PER_UNIT_KG,
                        PRODUCT_PACKAGING_COMPONENTS.CLASSIFIER_SOURCE,
                        PRODUCT_PACKAGING_COMPONENTS.COMPONENT_ORDER,
                        PRODUCT_PACKAGING_COMPONENTS.MATERIAL_DESCRIPTION)
                .from(PRODUCTS)
                .leftJoin(PRODUCT_PACKAGING_COMPONENTS)
                .on(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID.eq(PRODUCTS.ID))
                .where(PRODUCTS.TENANT_ID.eq(tenantId))
                .and(PRODUCTS.STATUS.eq("ACTIVE"))
                .and(PRODUCTS.EPR_SCOPE.in("FIRST_PLACER", "UNKNOWN"))
                .fetch(r -> new AggregationRow(
                        r.get(PRODUCTS.ID),
                        r.get(PRODUCTS.VTSZ),
                        r.get(PRODUCTS.NAME),
                        r.get(PRODUCTS.REVIEW_STATE),
                        r.get(PRODUCTS.EPR_SCOPE),
                        r.get(compId),
                        r.get(PRODUCT_PACKAGING_COMPONENTS.KF_CODE),
                        r.get(PRODUCT_PACKAGING_COMPONENTS.WRAPPING_LEVEL),
                        r.get(PRODUCT_PACKAGING_COMPONENTS.ITEMS_PER_PARENT),
                        r.get(PRODUCT_PACKAGING_COMPONENTS.WEIGHT_PER_UNIT_KG),
                        r.get(PRODUCT_PACKAGING_COMPONENTS.CLASSIFIER_SOURCE),
                        r.get(PRODUCT_PACKAGING_COMPONENTS.COMPONENT_ORDER),
                        r.get(PRODUCT_PACKAGING_COMPONENTS.MATERIAL_DESCRIPTION)
                ));
    }

    /**
     * Returns RESELLER products that had OUTBOUND invoice line-item traffic in the given period —
     * purely informational, surfaced on the filing page "Viszonteladóként kizárt termékek" panel
     * (Story 10.11 AC #6 + AC #20).
     *
     * <p>Matches the aggregator's VTSZ + product-name matching rule by joining
     * {@code product_packaging_components}-sourced invoice lines via the {@code products} table.
     * Since the aggregator's line-item source is NAV invoices (not a DB table), the period filter
     * degrades to "products that the caller lists as sold in the period" — computed at call-time by
     * passing sold product IDs from the aggregation result. This method intentionally does NOT
     * re-query NAV; it reads only the Registry + accepts the set of product IDs the caller observed
     * in the period.
     */
    public List<ExcludedProductRow> loadExcludedResellerProducts(UUID tenantId, java.util.Set<UUID> soldProductIds) {
        if (soldProductIds == null || soldProductIds.isEmpty()) return List.of();

        return dsl.select(PRODUCTS.ID, PRODUCTS.VTSZ, PRODUCTS.NAME, PRODUCTS.ARTICLE_NUMBER)
                .from(PRODUCTS)
                .where(PRODUCTS.TENANT_ID.eq(tenantId))
                .and(PRODUCTS.EPR_SCOPE.eq("RESELLER"))
                .and(PRODUCTS.ID.in(soldProductIds))
                .and(PRODUCTS.STATUS.eq("ACTIVE"))
                .orderBy(PRODUCTS.VTSZ.asc(), PRODUCTS.NAME.asc())
                .fetch(r -> new ExcludedProductRow(
                        r.get(PRODUCTS.ID),
                        r.get(PRODUCTS.VTSZ),
                        r.get(PRODUCTS.NAME),
                        r.get(PRODUCTS.ARTICLE_NUMBER),
                        0, BigDecimal.ZERO));
    }

    /**
     * Registry-only variant of {@link #loadExcludedResellerProducts} that does NOT require a
     * caller-supplied sold-product set: returns ALL RESELLER products for the tenant. Used when the
     * caller wants to populate the "excluded" panel even before the aggregation has completed.
     */
    public List<ExcludedProductRow> loadResellerProducts(UUID tenantId) {
        return dsl.select(PRODUCTS.ID, PRODUCTS.VTSZ, PRODUCTS.NAME, PRODUCTS.ARTICLE_NUMBER)
                .from(PRODUCTS)
                .where(PRODUCTS.TENANT_ID.eq(tenantId))
                .and(PRODUCTS.EPR_SCOPE.eq("RESELLER"))
                .and(PRODUCTS.STATUS.eq("ACTIVE"))
                .orderBy(PRODUCTS.VTSZ.asc(), PRODUCTS.NAME.asc())
                .fetch(r -> new ExcludedProductRow(
                        r.get(PRODUCTS.ID),
                        r.get(PRODUCTS.VTSZ),
                        r.get(PRODUCTS.NAME),
                        r.get(PRODUCTS.ARTICLE_NUMBER),
                        0, BigDecimal.ZERO));
    }

    public record ExcludedProductRow(
            UUID productId,
            String vtsz,
            String name,
            String articleNumber,
            int invoiceLineCount,
            BigDecimal totalUnitsSold
    ) {}

    // ─── Scope mutations (Story 10.11) ───────────────────────────────────────

    /**
     * Update {@code epr_scope} on a single product; returns number of rows affected.
     * <p>
     * Guards against TOCTOU: the WHERE clause explicitly rejects {@code ARCHIVED} rows so a
     * concurrent archive between the service-layer read and this write cannot slip an update
     * past the 409 guard. Returns 0 for any of: tenant mismatch, archived row, missing id.
     */
    public int updateEprScope(UUID productId, UUID tenantId, String scope) {
        return dsl.update(PRODUCTS)
                .set(PRODUCTS.EPR_SCOPE, scope)
                .set(PRODUCTS.UPDATED_AT, OffsetDateTime.now())
                .where(PRODUCTS.ID.eq(productId))
                .and(PRODUCTS.TENANT_ID.eq(tenantId))
                .and(PRODUCTS.STATUS.ne(ProductStatus.ARCHIVED.name()))
                .execute();
    }

    /** Load (productId, currentScope, status) for a set of products — used by bulk PATCH endpoint. */
    public List<ProductScopeRow> loadProductScopes(UUID tenantId, java.util.Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) return List.of();
        return dsl.select(PRODUCTS.ID, PRODUCTS.EPR_SCOPE, PRODUCTS.STATUS)
                .from(PRODUCTS)
                .where(PRODUCTS.TENANT_ID.eq(tenantId))
                .and(PRODUCTS.ID.in(productIds))
                .fetch(r -> new ProductScopeRow(
                        r.get(PRODUCTS.ID),
                        r.get(PRODUCTS.EPR_SCOPE),
                        r.get(PRODUCTS.STATUS)));
    }

    public record ProductScopeRow(UUID productId, String currentScope, String status) {}

    /**
     * Returns total non-archived products and how many have at least one component with a non-null kf_code.
     * Used by the registry summary endpoint (Story 10.7).
     */
    public RegistrySummary countSummary(UUID tenantId) {
        var hasKfCode = DSL.exists(
                DSL.selectOne()
                        .from(PRODUCT_PACKAGING_COMPONENTS)
                        .where(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID.eq(PRODUCTS.ID))
                        .and(PRODUCT_PACKAGING_COMPONENTS.KF_CODE.isNotNull())
        );

        var result = dsl
                .select(
                        DSL.count().as("total_products"),
                        DSL.count().filterWhere(hasKfCode).as("products_with_components")
                )
                .from(PRODUCTS)
                .where(PRODUCTS.TENANT_ID.eq(tenantId))
                .and(PRODUCTS.STATUS.ne("ARCHIVED"))
                .fetchOne();

        if (result == null) return new RegistrySummary(0, 0);
        return new RegistrySummary(
                result.get("total_products", Integer.class),
                result.get("products_with_components", Integer.class)
        );
    }

    public record RegistrySummary(int totalProducts, int productsWithComponents) {}

    /** MAX(updated_at) across products and components for a tenant — used as the Caffeine cache key signal. */
    public OffsetDateTime resolveMaxUpdatedAt(UUID tenantId) {
        OffsetDateTime productsMax = dsl.select(max(PRODUCTS.UPDATED_AT))
                .from(PRODUCTS)
                .where(PRODUCTS.TENANT_ID.eq(tenantId))
                .fetchOne(0, OffsetDateTime.class);

        OffsetDateTime componentsMax = dsl.select(max(PRODUCT_PACKAGING_COMPONENTS.UPDATED_AT))
                .from(PRODUCT_PACKAGING_COMPONENTS)
                .join(PRODUCTS).on(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID.eq(PRODUCTS.ID))
                .where(PRODUCTS.TENANT_ID.eq(tenantId))
                .fetchOne(0, OffsetDateTime.class);

        if (productsMax == null && componentsMax == null) {
            return OffsetDateTime.parse("2020-01-01T00:00:00Z");
        }
        if (productsMax == null) return componentsMax;
        if (componentsMax == null) return productsMax;
        return productsMax.isAfter(componentsMax) ? productsMax : componentsMax;
    }

    // ─── Domain mappers ──────────────────────────────────────────────────────

    public static Product toProduct(ProductsRecord p, List<ProductPackagingComponentsRecord> comps) {
        return new Product(
                p.getId(), p.getTenantId(), p.getArticleNumber(), p.getName(),
                p.getVtsz(), p.getPrimaryUnit(),
                ProductStatus.valueOf(p.getStatus()),
                p.getEprScope(),
                comps.stream().map(RegistryRepository::toComponent).toList(),
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }

    public static ProductPackagingComponent toComponent(ProductPackagingComponentsRecord r) {
        return new ProductPackagingComponent(
                r.getId(), r.getProductId(), r.getMaterialDescription(), r.getKfCode(),
                r.getWeightPerUnitKg(), r.getComponentOrder(),
                r.getItemsPerParent() != null ? r.getItemsPerParent() : new BigDecimal("1.0000"),
                r.getWrappingLevel() != null ? r.getWrappingLevel() : 1,
                r.getMaterialTemplateId(),
                r.getRecyclabilityGrade() != null ? RecyclabilityGrade.valueOf(r.getRecyclabilityGrade()) : null,
                r.getRecycledContentPct(), r.getReusable(),
                fromJsonb(r.getSubstancesOfConcern()),
                r.getSupplierDeclarationRef(),
                r.getCreatedAt(), r.getUpdatedAt()
        );
    }

    // ─── JSONB helpers ───────────────────────────────────────────────────────

    private static JSONB toJsonb(JsonNode node) {
        if (node == null) return null;
        try {
            return JSONB.jsonb(MAPPER.writeValueAsString(node));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize substancesOfConcern to JSON", e);
        }
    }

    static JsonNode fromJsonb(JSONB jsonb) {
        if (jsonb == null) return null;
        try {
            return MAPPER.readTree(jsonb.data());
        } catch (JsonProcessingException e) {
            LOGGER.warn("Failed to parse substances_of_concern JSONB: {}", e.getMessage());
            return null;
        }
    }
}
