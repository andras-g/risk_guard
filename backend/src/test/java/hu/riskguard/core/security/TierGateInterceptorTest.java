package hu.riskguard.core.security;

import hu.riskguard.core.exception.TierUpgradeRequiredException;
import hu.riskguard.identity.domain.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TierGateInterceptorTest {

    @Mock
    private IdentityService identityService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private TierGateInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new TierGateInterceptor(identityService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private HandlerMethod createHandlerMethod(String methodName) throws Exception {
        Method method = TestEndpoints.class.getMethod(methodName);
        return new HandlerMethod(new TestEndpoints(), method);
    }

    // Test endpoints class with various tier annotations
    static class TestEndpoints {
        @TierRequired(Tier.ALAP)
        public void alapEndpoint() {}

        @TierRequired(Tier.PRO)
        public void proEndpoint() {}

        @TierRequired(Tier.PRO_EPR)
        public void proEprEndpoint() {}

        public void openEndpoint() {}
    }

    @Nested
    class SufficientTier {

        @Test
        void proUserAccessingProEndpoint_shouldAllow() throws Exception {
            UUID tenantId = UUID.randomUUID();
            TenantContext.setCurrentTenant(tenantId);
            when(identityService.findTenantTier(tenantId)).thenReturn("PRO");

            boolean result = interceptor.preHandle(request, response, createHandlerMethod("proEndpoint"));

            assertThat(result).isTrue();
        }

        @Test
        void proEprUserAccessingProEndpoint_shouldAllow() throws Exception {
            UUID tenantId = UUID.randomUUID();
            TenantContext.setCurrentTenant(tenantId);
            when(identityService.findTenantTier(tenantId)).thenReturn("PRO_EPR");

            boolean result = interceptor.preHandle(request, response, createHandlerMethod("proEndpoint"));

            assertThat(result).isTrue();
        }

        @Test
        void alapUserAccessingAlapEndpoint_shouldAllow() throws Exception {
            UUID tenantId = UUID.randomUUID();
            TenantContext.setCurrentTenant(tenantId);
            when(identityService.findTenantTier(tenantId)).thenReturn("ALAP");

            boolean result = interceptor.preHandle(request, response, createHandlerMethod("alapEndpoint"));

            assertThat(result).isTrue();
        }
    }

    @Nested
    class InsufficientTier {

        @Test
        void alapUserAccessingProEndpoint_shouldThrow() throws Exception {
            UUID tenantId = UUID.randomUUID();
            TenantContext.setCurrentTenant(tenantId);
            when(identityService.findTenantTier(tenantId)).thenReturn("ALAP");

            HandlerMethod handler = createHandlerMethod("proEndpoint");

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                    .isInstanceOf(TierUpgradeRequiredException.class)
                    .satisfies(ex -> {
                        TierUpgradeRequiredException e = (TierUpgradeRequiredException) ex;
                        assertThat(e.getRequiredTier()).isEqualTo(Tier.PRO);
                        assertThat(e.getCurrentTier()).isEqualTo(Tier.ALAP);
                    });
        }

        @Test
        void proUserAccessingProEprEndpoint_shouldThrow() throws Exception {
            UUID tenantId = UUID.randomUUID();
            TenantContext.setCurrentTenant(tenantId);
            when(identityService.findTenantTier(tenantId)).thenReturn("PRO");

            HandlerMethod handler = createHandlerMethod("proEprEndpoint");

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                    .isInstanceOf(TierUpgradeRequiredException.class)
                    .satisfies(ex -> {
                        TierUpgradeRequiredException e = (TierUpgradeRequiredException) ex;
                        assertThat(e.getRequiredTier()).isEqualTo(Tier.PRO_EPR);
                        assertThat(e.getCurrentTier()).isEqualTo(Tier.PRO);
                    });
        }
    }

    @Nested
    class FailClosed {

        @Test
        void dbErrorShouldThrowWithNullCurrentTier() throws Exception {
            UUID tenantId = UUID.randomUUID();
            TenantContext.setCurrentTenant(tenantId);
            when(identityService.findTenantTier(tenantId)).thenThrow(new RuntimeException("DB connection lost"));

            HandlerMethod handler = createHandlerMethod("proEndpoint");

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                    .isInstanceOf(TierUpgradeRequiredException.class)
                    .satisfies(ex -> {
                        TierUpgradeRequiredException e = (TierUpgradeRequiredException) ex;
                        assertThat(e.getRequiredTier()).isEqualTo(Tier.PRO);
                        assertThat(e.getCurrentTier()).isNull();
                    });
        }

        @Test
        void nullTierResultShouldThrowWithNullCurrentTier() throws Exception {
            UUID tenantId = UUID.randomUUID();
            TenantContext.setCurrentTenant(tenantId);
            when(identityService.findTenantTier(tenantId)).thenReturn(null);

            HandlerMethod handler = createHandlerMethod("proEndpoint");

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                    .isInstanceOf(TierUpgradeRequiredException.class)
                    .satisfies(ex -> {
                        TierUpgradeRequiredException e = (TierUpgradeRequiredException) ex;
                        assertThat(e.getCurrentTier()).isNull();
                    });
        }

        @Test
        void noTenantContextShouldThrow() throws Exception {
            // TenantContext not set — simulates unauthenticated or broken filter chain
            when(request.getRequestURI()).thenReturn("/api/v1/test");

            HandlerMethod handler = createHandlerMethod("proEndpoint");

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                    .isInstanceOf(TierUpgradeRequiredException.class)
                    .satisfies(ex -> {
                        TierUpgradeRequiredException e = (TierUpgradeRequiredException) ex;
                        assertThat(e.getCurrentTier()).isNull();
                    });
        }
    }

    @Nested
    class PassThrough {

        @Test
        void noAnnotationShouldPassThrough() throws Exception {
            boolean result = interceptor.preHandle(request, response, createHandlerMethod("openEndpoint"));

            assertThat(result).isTrue();
            verifyNoInteractions(identityService);
        }

        @Test
        void nonHandlerMethodShouldPassThrough() throws Exception {
            // e.g., static resource handler
            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            verifyNoInteractions(identityService);
        }
    }
}
