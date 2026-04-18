package hu.riskguard.epr.registry.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.riskguard.core.repository.BaseRepository;
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
        UUID id = dsl.insertInto(PRODUCTS)
                .set(PRODUCTS.TENANT_ID, tenantId)
                .set(PRODUCTS.ARTICLE_NUMBER, cmd.articleNumber())
                .set(PRODUCTS.NAME, cmd.name())
                .set(PRODUCTS.VTSZ, cmd.vtsz())
                .set(PRODUCTS.PRIMARY_UNIT, cmd.primaryUnit())
                .set(PRODUCTS.STATUS, cmd.status().name())
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

        return dsl.select(
                        PRODUCTS.ID,
                        PRODUCTS.TENANT_ID,
                        PRODUCTS.ARTICLE_NUMBER,
                        PRODUCTS.NAME,
                        PRODUCTS.VTSZ,
                        PRODUCTS.PRIMARY_UNIT,
                        PRODUCTS.STATUS,
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

    // ─── Domain mappers ──────────────────────────────────────────────────────

    public static Product toProduct(ProductsRecord p, List<ProductPackagingComponentsRecord> comps) {
        return new Product(
                p.getId(), p.getTenantId(), p.getArticleNumber(), p.getName(),
                p.getVtsz(), p.getPrimaryUnit(),
                ProductStatus.valueOf(p.getStatus()),
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
