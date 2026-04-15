package hu.riskguard.epr.registry.domain;

import hu.riskguard.epr.registry.internal.RegistryRepository;
import hu.riskguard.jooq.tables.records.ProductPackagingComponentsRecord;
import hu.riskguard.jooq.tables.records.ProductsRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RegistryLookupService}.
 * Critical: includes cross-tenant isolation tests to verify no data leakage.
 */
@ExtendWith(MockitoExtension.class)
class RegistryLookupServiceTest {

    @Mock
    private RegistryRepository registryRepository;

    private RegistryLookupService service;

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RegistryLookupService(registryRepository);
    }

    @Test
    void null_tenantId_throws_immediately() {
        assertThatThrownBy(() -> service.findByVtszOrArticleNumber(null, "12345678", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenantId must not be null");
    }

    @Test
    void returns_empty_when_no_match_for_vtsz() {
        when(registryRepository.findActiveByVtsz(TENANT_A, "12345678"))
                .thenReturn(List.of());
        Optional<RegistryMatch> result = service.findByVtszOrArticleNumber(TENANT_A, "12345678", null);
        assertThat(result).isEmpty();
    }

    @Test
    void returns_match_when_vtsz_found() {
        ProductsRecord product = stubProduct(PRODUCT_ID, TENANT_A, "12345678");
        ProductPackagingComponentsRecord component = stubComponent(PRODUCT_ID, "CS01012B", new BigDecimal("0.100"));
        when(registryRepository.findActiveByVtsz(TENANT_A, "12345678")).thenReturn(List.of(product));
        when(registryRepository.findComponentsByProductAndTenant(PRODUCT_ID, TENANT_A))
                .thenReturn(List.of(component));

        Optional<RegistryMatch> result = service.findByVtszOrArticleNumber(TENANT_A, "12345678", null);
        assertThat(result).isPresent();
        assertThat(result.get().productId()).isEqualTo(PRODUCT_ID);
        assertThat(result.get().components()).hasSize(1);
    }

    @Test
    void article_number_takes_priority_over_vtsz() {
        UUID productByArticle = UUID.randomUUID();
        ProductsRecord articleProduct = stubProduct(productByArticle, TENANT_A, "12345678");
        when(registryRepository.findActiveByArticleNumber(TENANT_A, "SKU-001"))
                .thenReturn(Optional.of(articleProduct));
        when(registryRepository.findComponentsByProductAndTenant(productByArticle, TENANT_A))
                .thenReturn(List.of());

        Optional<RegistryMatch> result = service.findByVtszOrArticleNumber(TENANT_A, "12345678", "SKU-001");
        assertThat(result).isPresent();
        assertThat(result.get().productId()).isEqualTo(productByArticle);
        // VTSZ lookup should NOT have been called
        verify(registryRepository, never()).findActiveByVtsz(any(), any());
    }

    @Test
    void cross_tenant_isolation_tenant_b_cannot_see_tenant_a_products() {
        // Tenant B query must ONLY query with TENANT_B — returns empty (isolation: TENANT_A data not returned)
        when(registryRepository.findActiveByVtsz(TENANT_B, "12345678")).thenReturn(List.of());

        Optional<RegistryMatch> resultB = service.findByVtszOrArticleNumber(TENANT_B, "12345678", null);
        assertThat(resultB).isEmpty();
        // Verify repository was called with TENANT_B, never with TENANT_A
        verify(registryRepository).findActiveByVtsz(TENANT_B, "12345678");
        verify(registryRepository, never()).findActiveByVtsz(TENANT_A, "12345678");
    }

    @Test
    void multi_active_same_vtsz_picks_first_from_repository_ordering() {
        UUID olderId = UUID.randomUUID();
        UUID newerId = UUID.randomUUID();
        ProductsRecord older = stubProduct(olderId, TENANT_A, "12345678");
        ProductsRecord newer = stubProduct(newerId, TENANT_A, "12345678");
        // Repository contract: list is ordered by updated_at ASC so older first
        when(registryRepository.findActiveByVtsz(TENANT_A, "12345678"))
                .thenReturn(List.of(older, newer));
        when(registryRepository.findComponentsByProductAndTenant(olderId, TENANT_A))
                .thenReturn(List.of());

        Optional<RegistryMatch> result = service.findByVtszOrArticleNumber(TENANT_A, "12345678", null);
        assertThat(result).isPresent();
        assertThat(result.get().productId()).isEqualTo(olderId);
        // Ensure we did NOT also load components for the newer product
        verify(registryRepository, never()).findComponentsByProductAndTenant(newerId, TENANT_A);
    }

    @Test
    void blank_vtsz_and_article_returns_empty() {
        Optional<RegistryMatch> result = service.findByVtszOrArticleNumber(TENANT_A, "", "");
        assertThat(result).isEmpty();
        verifyNoInteractions(registryRepository);
    }

    // ─── Stub helpers ─────────────────────────────────────────────────────────

    private static ProductsRecord stubProduct(UUID id, UUID tenantId, String vtsz) {
        ProductsRecord r = new ProductsRecord();
        r.setId(id);
        r.setTenantId(tenantId);
        r.setVtsz(vtsz);
        r.setName("Test product");
        r.setStatus(ProductStatus.ACTIVE.name());
        r.setPrimaryUnit("pcs");
        return r;
    }

    private static ProductPackagingComponentsRecord stubComponent(UUID productId, String kfCode,
                                                                    BigDecimal weight) {
        ProductPackagingComponentsRecord r = new ProductPackagingComponentsRecord();
        r.setId(UUID.randomUUID());
        r.setProductId(productId);
        r.setKfCode(kfCode);
        r.setWeightPerUnitKg(weight);
        r.setMaterialDescription("Test material");
        r.setComponentOrder(1);
        return r;
    }
}
