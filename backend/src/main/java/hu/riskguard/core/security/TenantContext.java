package hu.riskguard.core.security;

import java.util.UUID;

public class TenantContext {

    /**
     * HTTP request path. Bind in TenantFilter. Background services use setCurrentTenant().
     */
    public static final ScopedValue<UUID> CURRENT_TENANT = ScopedValue.newInstance();

    private static final ThreadLocal<UUID> currentTenant = new ThreadLocal<>();

    @Deprecated(since = "6.0", forRemoval = false)
    public static void setCurrentTenant(UUID tenantId) {
        currentTenant.set(tenantId);
    }

    public static UUID getCurrentTenant() {
        return CURRENT_TENANT.isBound() ? CURRENT_TENANT.get() : currentTenant.get();
    }

    @Deprecated(since = "6.0", forRemoval = false)
    public static void clear() {
        currentTenant.remove();
    }
}
