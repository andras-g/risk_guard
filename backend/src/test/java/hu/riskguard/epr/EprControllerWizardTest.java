package hu.riskguard.epr;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.riskguard.core.exception.TierGateExceptionHandler;
import hu.riskguard.core.security.TenantContext;
import hu.riskguard.core.security.TierGateInterceptor;
import hu.riskguard.epr.api.EprController;
import hu.riskguard.epr.api.dto.*;
import hu.riskguard.epr.domain.EprService;
import hu.riskguard.identity.domain.IdentityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for {@link EprController} wizard endpoints.
 *
 * <p>Uses standalone MockMvc with the real {@link TierGateInterceptor} to verify:
 * - Correct HTTP status codes for all wizard endpoints
 * - PRO_EPR tier gating returns 403 for lower-tier tenants
 * - {@code @TierRequired} annotation is enforced via the interceptor
 * - Tenant isolation via JWT {@code active_tenant_id} claim
 */
@ExtendWith(MockitoExtension.class)
class EprControllerWizardTest {

    @Mock
    private EprService eprService;

    @Mock
    private IdentityService identityService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID TENANT_ID = UUID.randomUUID();

    // Thread-local security context controlled per test
    private static final ThreadLocal<SecurityContext> TEST_SECURITY_CONTEXT = new ThreadLocal<>();

    /**
     * Filter that applies the per-test SecurityContext to SecurityContextHolder.
     * This replaces Spring Security's SecurityContextPersistenceFilter in standalone MockMvc.
     */
    private static class SecurityContextFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            SecurityContext ctx = TEST_SECURITY_CONTEXT.get();
            if (ctx != null) {
                SecurityContextHolder.setContext(ctx);
            }
            try {
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    /**
     * Custom argument resolver that injects the Jwt from the current SecurityContext.
     * Required in standalone MockMvc which does not configure Spring Security's
     * full argument resolver chain for @AuthenticationPrincipal.
     */
    private static class JwtArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return Jwt.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtToken) {
                return jwtToken.getToken();
            }
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        TierGateInterceptor interceptor = new TierGateInterceptor(identityService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EprController(eprService))
                .addFilters(new SecurityContextFilter())
                .addInterceptors(interceptor)
                .setControllerAdvice(new TierGateExceptionHandler())
                .setCustomArgumentResolvers(new JwtArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        TEST_SECURITY_CONTEXT.remove();
    }

    // ─── Tier gating ─────────────────────────────────────────────────────────

    @Nested
    class TierGating {

        @Test
        void wizardStart_withoutProEprTier_returns403() throws Exception {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(identityService.findTenantTier(TENANT_ID)).thenReturn("ALAP");
            TEST_SECURITY_CONTEXT.set(buildSecurityContext(TENANT_ID));

            mockMvc.perform(get("/api/v1/epr/wizard/start"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.requiredTier").value("PRO_EPR"))
                    .andExpect(jsonPath("$.currentTier").value("ALAP"));
        }

        @Test
        void wizardStep_withProTierOnly_returns403() throws Exception {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(identityService.findTenantTier(TENANT_ID)).thenReturn("PRO");
            TEST_SECURITY_CONTEXT.set(buildSecurityContext(TENANT_ID));

            WizardStepRequest request = new WizardStepRequest(
                    1, List.of(), new WizardSelection("product_stream", "11", "Packaging"));

            mockMvc.perform(post("/api/v1/epr/wizard/step")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.requiredTier").value("PRO_EPR"));
        }

        @Test
        void wizardStart_withProEprTier_returns200() throws Exception {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(identityService.findTenantTier(TENANT_ID)).thenReturn("PRO_EPR");
            when(eprService.getActiveConfigVersion()).thenReturn(1);
            when(eprService.startWizard(eq(1), anyString()))
                    .thenReturn(new WizardStartResponse(1, "product_stream", List.of()));
            TEST_SECURITY_CONTEXT.set(buildSecurityContext(TENANT_ID));

            mockMvc.perform(get("/api/v1/epr/wizard/start"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.configVersion").value(1))
                    .andExpect(jsonPath("$.level").value("product_stream"));
        }
    }

    // ─── Wizard endpoint functional tests ────────────────────────────────────

    @Nested
    class WizardStart {

        @Test
        void returnsProductStreamsWithExplicitVersion() throws Exception {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(identityService.findTenantTier(TENANT_ID)).thenReturn("PRO_EPR");
            WizardStartResponse expected = new WizardStartResponse(
                    1, "product_stream",
                    List.of(new WizardOption("11", "Packaging", null)));
            when(eprService.startWizard(eq(1), anyString())).thenReturn(expected);
            TEST_SECURITY_CONTEXT.set(buildSecurityContext(TENANT_ID));

            mockMvc.perform(get("/api/v1/epr/wizard/start").param("configVersion", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.configVersion").value(1))
                    .andExpect(jsonPath("$.options[0].code").value("11"));
        }

        @Test
        void usesActiveVersionWhenParamAbsent() throws Exception {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(identityService.findTenantTier(TENANT_ID)).thenReturn("PRO_EPR");
            when(eprService.getActiveConfigVersion()).thenReturn(1);
            when(eprService.startWizard(eq(1), anyString()))
                    .thenReturn(new WizardStartResponse(1, "product_stream", List.of()));
            TEST_SECURITY_CONTEXT.set(buildSecurityContext(TENANT_ID));

            mockMvc.perform(get("/api/v1/epr/wizard/start"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.configVersion").value(1));
        }
    }

    @Nested
    class WizardStep {

        @Test
        void returnsNextLevelOptions() throws Exception {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(identityService.findTenantTier(TENANT_ID)).thenReturn("PRO_EPR");
            TEST_SECURITY_CONTEXT.set(buildSecurityContext(TENANT_ID));

            WizardStepRequest request = new WizardStepRequest(
                    1, List.of(),
                    new WizardSelection("product_stream", "11", "Packaging"));
            WizardStepResponse expected = new WizardStepResponse(
                    1, "product_stream", "material_stream",
                    List.of(new WizardOption("01", "Paper", null)),
                    List.of(new WizardSelection("product_stream", "11", "Packaging")),
                    false);
            when(eprService.processStep(eq(request), anyString())).thenReturn(expected);

            mockMvc.perform(post("/api/v1/epr/wizard/step")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nextLevel").value("material_stream"))
                    .andExpect(jsonPath("$.options[0].code").value("01"));
        }
    }

    @Nested
    class WizardResolve {

        @Test
        void returnsResolvedKfCode() throws Exception {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(identityService.findTenantTier(TENANT_ID)).thenReturn("PRO_EPR");
            TEST_SECURITY_CONTEXT.set(buildSecurityContext(TENANT_ID));

            List<WizardSelection> traversal = List.of(
                    new WizardSelection("product_stream", "11", "Packaging"),
                    new WizardSelection("material_stream", "01", "Paper"),
                    new WizardSelection("group", "01", "Consumer"),
                    new WizardSelection("subgroup", "01", "Default"));
            WizardResolveRequest request = new WizardResolveRequest(1, traversal);
            WizardResolveResponse expected = new WizardResolveResponse(
                    "11010101", "1101", new BigDecimal("20.44"), "HUF",
                    "Paper packaging", traversal, "33/2025 EM rendelet",
                    "HIGH", "full_traversal");
            when(eprService.resolveKfCode(eq(traversal), eq(1), anyString())).thenReturn(expected);

            mockMvc.perform(post("/api/v1/epr/wizard/resolve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.kfCode").value("11010101"))
                    .andExpect(jsonPath("$.feeCode").value("1101"))
                    .andExpect(jsonPath("$.feeRate").value(20.44));
        }
    }

    @Nested
    class WizardKfCodes {

        @Test
        void returnsKfCodeList() throws Exception {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(identityService.findTenantTier(TENANT_ID)).thenReturn("PRO_EPR");
            when(eprService.getActiveConfigVersion()).thenReturn(1);
            TEST_SECURITY_CONTEXT.set(buildSecurityContext(TENANT_ID));

            KfCodeListResponse expected = new KfCodeListResponse(1,
                    List.of(new KfCodeEntry("11010101", "1101", new BigDecimal("20.44"),
                            "HUF", "Paper packaging", "Non-deposit packaging")));
            when(eprService.getAllKfCodes(eq(1), anyString())).thenReturn(expected);

            mockMvc.perform(get("/api/v1/epr/wizard/kf-codes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.configVersion").value(1))
                    .andExpect(jsonPath("$.entries[0].kfCode").value("11010101"));
        }

        @Test
        void respectsExplicitConfigVersion() throws Exception {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(identityService.findTenantTier(TENANT_ID)).thenReturn("PRO_EPR");
            TEST_SECURITY_CONTEXT.set(buildSecurityContext(TENANT_ID));

            KfCodeListResponse expected = new KfCodeListResponse(2, List.of());
            when(eprService.getAllKfCodes(eq(2), anyString())).thenReturn(expected);

            mockMvc.perform(get("/api/v1/epr/wizard/kf-codes").param("configVersion", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.configVersion").value(2));
        }
    }

    @Nested
    class WizardConfirm {

        @Test
        void persistsAndReturnsConfirmation() throws Exception {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(identityService.findTenantTier(TENANT_ID)).thenReturn("PRO_EPR");
            TEST_SECURITY_CONTEXT.set(buildSecurityContext(TENANT_ID));

            UUID calcId = UUID.randomUUID();
            WizardConfirmRequest request = new WizardConfirmRequest(
                    1,
                    List.of(new WizardSelection("product_stream", "11", "Packaging")),
                    "11010101",
                    new BigDecimal("20.44"),
                    "Paper packaging",
                    UUID.randomUUID(),
                    "HIGH",
                    null,
                    null);
            WizardConfirmResponse expected = new WizardConfirmResponse(calcId, "11010101", true);
            when(eprService.confirmWizard(any(WizardConfirmRequest.class), eq(TENANT_ID)))
                    .thenReturn(expected);

            mockMvc.perform(post("/api/v1/epr/wizard/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.kfCode").value("11010101"))
                    .andExpect(jsonPath("$.templateUpdated").value(true));
        }

        @Test
        void confirmWithOverrideFieldsSerializesCorrectly() throws Exception {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(identityService.findTenantTier(TENANT_ID)).thenReturn("PRO_EPR");
            TEST_SECURITY_CONTEXT.set(buildSecurityContext(TENANT_ID));

            UUID calcId = UUID.randomUUID();
            UUID templateId = UUID.randomUUID();
            WizardConfirmRequest request = new WizardConfirmRequest(
                    1,
                    List.of(new WizardSelection("product_stream", "11", "Packaging")),
                    "11010101",
                    new BigDecimal("20.44"),
                    "Paper packaging",
                    templateId,
                    "MEDIUM",
                    "11020101",
                    "Better classification match");
            WizardConfirmResponse expected = new WizardConfirmResponse(calcId, "11020101", true);
            when(eprService.confirmWizard(any(WizardConfirmRequest.class), eq(TENANT_ID)))
                    .thenReturn(expected);

            mockMvc.perform(post("/api/v1/epr/wizard/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.kfCode").value("11020101"))
                    .andExpect(jsonPath("$.templateUpdated").value(true));
        }

        @Test
        void missingTenantIdInJwtReturns401() throws Exception {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(identityService.findTenantTier(TENANT_ID)).thenReturn("PRO_EPR");

            // JWT without active_tenant_id claim — controller's requireUuidClaim throws 401
            Jwt jwtNoTenant = Jwt.withTokenValue("test-token")
                    .header("alg", "none")
                    .subject("test@test.com")
                    .build();
            var ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(new JwtAuthenticationToken(jwtNoTenant,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            TEST_SECURITY_CONTEXT.set(ctx);

            WizardConfirmRequest request = new WizardConfirmRequest(
                    1, List.of(), "11010101", new BigDecimal("20.44"), "Paper", null,
                    "HIGH", null, null);

            mockMvc.perform(post("/api/v1/epr/wizard/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─── Retry Link tests ──────────────────────────────────────────────────

    @Nested
    class WizardRetryLink {

        @Test
        void retryLink_happyPath_returnsTemplateUpdated() throws Exception {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(identityService.findTenantTier(TENANT_ID)).thenReturn("PRO_EPR");
            TEST_SECURITY_CONTEXT.set(buildSecurityContext(TENANT_ID));

            UUID calcId = UUID.randomUUID();
            UUID templateId = UUID.randomUUID();
            RetryLinkRequest request = new RetryLinkRequest(calcId, templateId);
            RetryLinkResponse expected = new RetryLinkResponse(true, "11010101");
            when(eprService.retryLink(calcId, templateId, TENANT_ID)).thenReturn(expected);

            mockMvc.perform(post("/api/v1/epr/wizard/retry-link")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.templateUpdated").value(true))
                    .andExpect(jsonPath("$.kfCode").value("11010101"));
        }

        @Test
        void retryLink_calculationNotFound_returns404() throws Exception {
            TenantContext.setCurrentTenant(TENANT_ID);
            when(identityService.findTenantTier(TENANT_ID)).thenReturn("PRO_EPR");
            TEST_SECURITY_CONTEXT.set(buildSecurityContext(TENANT_ID));

            UUID calcId = UUID.randomUUID();
            UUID templateId = UUID.randomUUID();
            RetryLinkRequest request = new RetryLinkRequest(calcId, templateId);
            when(eprService.retryLink(calcId, templateId, TENANT_ID))
                    .thenThrow(new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND, "Calculation not found"));

            mockMvc.perform(post("/api/v1/epr/wizard/retry-link")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void retryLink_wrongTenant_returns404() throws Exception {
            UUID wrongTenantId = UUID.randomUUID();
            TenantContext.setCurrentTenant(wrongTenantId);
            when(identityService.findTenantTier(wrongTenantId)).thenReturn("PRO_EPR");
            TEST_SECURITY_CONTEXT.set(buildSecurityContext(wrongTenantId));

            UUID calcId = UUID.randomUUID();
            UUID templateId = UUID.randomUUID();
            RetryLinkRequest request = new RetryLinkRequest(calcId, templateId);
            when(eprService.retryLink(calcId, templateId, wrongTenantId))
                    .thenThrow(new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND, "Calculation not found"));

            mockMvc.perform(post("/api/v1/epr/wizard/retry-link")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private SecurityContext buildSecurityContext(UUID tenantId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("active_tenant_id", tenantId.toString())
                .claim("role", "SME_ADMIN")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        return ctx;
    }
}
