package hu.riskguard.epr.registry;

import hu.riskguard.epr.registry.api.RegistryController;
import hu.riskguard.epr.registry.api.dto.RegistrySummaryResponse;
import hu.riskguard.epr.registry.domain.RegistryService;
import hu.riskguard.epr.registry.internal.RegistryRepository.RegistrySummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GET /api/v1/registry/summary (AC #1–#4, #24).
 * Follows RegistryControllerTest pattern — direct controller invocation with Mockito.
 */
@ExtendWith(MockitoExtension.class)
class RegistrySummaryControllerTest {

    @Mock
    private RegistryService registryService;

    private RegistryController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new RegistryController(registryService);
    }

    // ─── Test 1: happy path — seeded tenant ──────────────────────────────────

    @Test
    void summary_seededTenant_returnsCorrectCounts() {
        when(registryService.getSummary(TENANT_ID))
                .thenReturn(new RegistrySummary(5, 3));

        RegistrySummaryResponse result = controller.summary(buildJwt(TENANT_ID));

        assertThat(result.totalProducts()).isEqualTo(5);
        assertThat(result.productsWithComponents()).isEqualTo(3);
    }

    // ─── Test 2: empty tenant returns zeros ──────────────────────────────────

    @Test
    void summary_emptyTenant_returnsZeros() {
        when(registryService.getSummary(TENANT_ID))
                .thenReturn(new RegistrySummary(0, 0));

        RegistrySummaryResponse result = controller.summary(buildJwt(TENANT_ID));

        assertThat(result.totalProducts()).isEqualTo(0);
        assertThat(result.productsWithComponents()).isEqualTo(0);
    }

    // ─── Test 3: products exist but none have kf_code components ─────────────

    @Test
    void summary_productsWithoutKfCode_productsWithComponentsIsZero() {
        when(registryService.getSummary(TENANT_ID))
                .thenReturn(new RegistrySummary(4, 0));

        RegistrySummaryResponse result = controller.summary(buildJwt(TENANT_ID));

        assertThat(result.totalProducts()).isEqualTo(4);
        assertThat(result.productsWithComponents()).isEqualTo(0);
    }

    // ─── Test 4: JWT tenant isolation — different tenant sees its own data ────

    @Test
    void summary_differentTenant_isolatedResult() {
        UUID otherTenant = UUID.randomUUID();
        when(registryService.getSummary(otherTenant))
                .thenReturn(new RegistrySummary(10, 7));

        RegistrySummaryResponse result = controller.summary(buildJwt(otherTenant));

        assertThat(result.totalProducts()).isEqualTo(10);
        assertThat(result.productsWithComponents()).isEqualTo(7);
        verify(registryService, never()).getSummary(TENANT_ID);
    }

    // ─── Test 5: controller delegates every call to the service ──────────────

    @Test
    void summary_everyRequest_delegatesToService() {
        // The controller holds no cache — cache lives inside RegistryService.
        // This test pins that contract: two incoming requests = two service calls.
        // The cache-hit behaviour is covered in RegistryServiceTest (spy on repository).
        when(registryService.getSummary(TENANT_ID))
                .thenReturn(new RegistrySummary(2, 1));

        controller.summary(buildJwt(TENANT_ID));
        controller.summary(buildJwt(TENANT_ID));

        verify(registryService, times(2)).getSummary(TENANT_ID);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Jwt buildJwt(UUID tenantId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("active_tenant_id", tenantId.toString())
                .claim("role", "SME_ADMIN")
                .build();
    }
}
