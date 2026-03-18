/**
 * Identity module — user, tenant, and mandate management.
 *
 * <p>Public API surface (named interfaces):
 * <ul>
 *   <li>{@code api} — REST controllers and DTOs</li>
 *   <li>{@code domain} — service facade ({@link hu.riskguard.identity.domain.IdentityService})
 *       and domain entities</li>
 * </ul>
 *
 * <p>{@code internal/} — repository and internal helpers (not accessible from other modules).
 */
package hu.riskguard.identity;
