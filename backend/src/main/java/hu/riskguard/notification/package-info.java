/**
 * Notification module — watchlist management, background monitoring, and email alerts.
 *
 * <p>Follows the module patterns established by the {@code screening} reference implementation:
 * <ul>
 *   <li>{@code api/} — REST controllers and DTOs (public API surface)</li>
 *   <li>{@code domain/} — service facade (public domain surface)</li>
 *   <li>{@code internal/} — repository and internal helpers (package-private)</li>
 * </ul>
 *
 * <p>Cross-module access is restricted to {@code api/} and {@code domain/} packages only.
 * The {@code internal/} package is enforced as inaccessible by Spring Modulith verification.
 */
package hu.riskguard.notification;
