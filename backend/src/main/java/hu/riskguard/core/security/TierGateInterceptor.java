package hu.riskguard.core.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import hu.riskguard.core.exception.TierUpgradeRequiredException;
import hu.riskguard.identity.domain.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.UUID;

/**
 * Spring MVC interceptor that enforces {@link TierRequired} annotations.
 * Runs AFTER authentication and TenantContext are established.
 *
 * <p>Uses Caffeine cache (5-min TTL, max 1000 entries) to avoid per-request DB lookups
 * for tenant tier. Fail-closed: if tier cannot be determined, access is denied.
 *
 * <p><strong>Known limitation:</strong> No explicit cache eviction mechanism exists.
 * When a tenant's tier is changed (e.g., via admin action), the cached tier may be stale
 * for up to 5 minutes. This is acceptable for the current MVP where tier changes are rare
 * admin operations. A future admin-tier-management story should expose a
 * {@code clearTierCache(UUID tenantId)} method for immediate cache invalidation on tier change.
 */
@Slf4j
@Component
public class TierGateInterceptor implements HandlerInterceptor {

    private final IdentityService identityService;
    private final Cache<UUID, String> tierCache;

    public TierGateInterceptor(IdentityService identityService) {
        this.identityService = identityService;
        this.tierCache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true; // Not a controller method — pass through
        }

        TierRequired annotation = findAnnotation(handlerMethod);
        if (annotation == null) {
            return true; // No tier requirement — pass through
        }

        Tier requiredTier = annotation.value();

        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.error("TierGateInterceptor: no TenantContext for tier-gated endpoint {}", request.getRequestURI());
            throw new TierUpgradeRequiredException(requiredTier, null);
        }

        Tier currentTier = resolveTier(tenantId, requiredTier);

        if (!currentTier.satisfies(requiredTier)) {
            throw new TierUpgradeRequiredException(requiredTier, currentTier);
        }

        return true;
    }

    private TierRequired findAnnotation(HandlerMethod handlerMethod) {
        // Method-level annotation takes precedence over class-level
        TierRequired methodAnnotation = handlerMethod.getMethodAnnotation(TierRequired.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        return handlerMethod.getBeanType().getAnnotation(TierRequired.class);
    }

    private Tier resolveTier(UUID tenantId, Tier requiredTier) {
        try {
            String tierStr = tierCache.get(tenantId, id -> identityService.findTenantTier(id));
            if (tierStr == null) {
                log.error("TierGateInterceptor: null tier for tenant {}", tenantId);
                throw new TierUpgradeRequiredException(requiredTier, null);
            }
            return Tier.valueOf(tierStr);
        } catch (TierUpgradeRequiredException e) {
            throw e; // Re-throw our own exception
        } catch (Exception e) {
            log.error("TierGateInterceptor: failed to resolve tier for tenant {}", tenantId, e);
            throw new TierUpgradeRequiredException(requiredTier, null);
        }
    }
}
