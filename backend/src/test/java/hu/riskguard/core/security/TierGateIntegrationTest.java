package hu.riskguard.core.security;

import hu.riskguard.core.config.TierGateConfig;
import hu.riskguard.core.exception.TierGateExceptionHandler;
import hu.riskguard.core.exception.TierUpgradeRequiredException;
import hu.riskguard.identity.domain.IdentityService;
import hu.riskguard.testing.TierGateTestController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for the tier gate interceptor using MockMvc standalone setup.
 * Verifies that the {@link TierGateInterceptor} correctly allows/denies access
 * when registered with real controller endpoints via {@link TierGateTestController}.
 *
 * <p>Uses standalone MockMvc (not @SpringBootTest) to avoid the pre-existing
 * Flyway duplicate-migration context loading issue.
 */
@ExtendWith(MockitoExtension.class)
class TierGateIntegrationTest {

    @Mock
    private IdentityService identityService;

    private MockMvc mockMvc;
    private TierGateInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new TierGateInterceptor(identityService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TierGateTestController())
                .addInterceptors(interceptor)
                .setControllerAdvice(new TierGateExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void setUpTenant(String tier) {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenant(tenantId);
        when(identityService.findTenantTier(tenantId)).thenReturn(tier);
    }

    @Nested
    class AlapUser {

        @Test
        void canAccessAlapEndpoint() throws Exception {
            setUpTenant("ALAP");

            mockMvc.perform(get("/api/v1/test-tier-gate/alap"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ALAP access granted"));
        }

        @Test
        void deniedProEndpointReturns403WithRfc7807() throws Exception {
            setUpTenant("ALAP");

            mockMvc.perform(get("/api/v1/test-tier-gate/pro"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value("urn:riskguard:error:tier-upgrade-required"))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.requiredTier").value("PRO"))
                    .andExpect(jsonPath("$.currentTier").value("ALAP"));
        }

        @Test
        void deniedProEprEndpointReturns403() throws Exception {
            setUpTenant("ALAP");

            mockMvc.perform(get("/api/v1/test-tier-gate/pro-epr"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.requiredTier").value("PRO_EPR"))
                    .andExpect(jsonPath("$.currentTier").value("ALAP"));
        }
    }

    @Nested
    class ProUser {

        @Test
        void canAccessAlapEndpoint() throws Exception {
            setUpTenant("PRO");

            mockMvc.perform(get("/api/v1/test-tier-gate/alap"))
                    .andExpect(status().isOk());
        }

        @Test
        void canAccessProEndpoint() throws Exception {
            setUpTenant("PRO");

            mockMvc.perform(get("/api/v1/test-tier-gate/pro"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("PRO access granted"));
        }

        @Test
        void deniedProEprEndpoint() throws Exception {
            setUpTenant("PRO");

            mockMvc.perform(get("/api/v1/test-tier-gate/pro-epr"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.requiredTier").value("PRO_EPR"))
                    .andExpect(jsonPath("$.currentTier").value("PRO"));
        }
    }

    @Nested
    class ProEprUser {

        @Test
        void canAccessAllTierEndpoints() throws Exception {
            setUpTenant("PRO_EPR");

            mockMvc.perform(get("/api/v1/test-tier-gate/alap"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/test-tier-gate/pro"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/test-tier-gate/pro-epr"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("PRO_EPR access granted"));
        }
    }

    @Nested
    class OpenEndpoint {

        @Test
        void accessibleWithoutTierContext() throws Exception {
            // No TenantContext set — open endpoint should still work
            mockMvc.perform(get("/api/v1/test-tier-gate/open"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("No tier required"));
        }
    }

    @Nested
    class FailClosed {

        @Test
        void nullTierFromDbReturns403() throws Exception {
            UUID tenantId = UUID.randomUUID();
            TenantContext.setCurrentTenant(tenantId);
            when(identityService.findTenantTier(tenantId)).thenReturn(null);

            mockMvc.perform(get("/api/v1/test-tier-gate/pro"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value("urn:riskguard:error:tier-upgrade-required"));
        }

        @Test
        void dbErrorReturns403() throws Exception {
            UUID tenantId = UUID.randomUUID();
            TenantContext.setCurrentTenant(tenantId);
            when(identityService.findTenantTier(tenantId)).thenThrow(new RuntimeException("DB connection lost"));

            mockMvc.perform(get("/api/v1/test-tier-gate/pro"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value("urn:riskguard:error:tier-upgrade-required"))
                    .andExpect(jsonPath("$.currentTier").isEmpty());
        }
    }

    @Nested
    class Rfc7807ResponseStructure {

        @Test
        void errorResponseContainsAllRequiredFields() throws Exception {
            setUpTenant("ALAP");

            mockMvc.perform(get("/api/v1/test-tier-gate/pro")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value("urn:riskguard:error:tier-upgrade-required"))
                    .andExpect(jsonPath("$.title").value("Tier upgrade required"))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.detail").isNotEmpty())
                    .andExpect(jsonPath("$.requiredTier").value("PRO"))
                    .andExpect(jsonPath("$.currentTier").value("ALAP"));
        }
    }
}
