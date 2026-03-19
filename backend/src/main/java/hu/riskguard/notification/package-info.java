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
 *
 * <p>Cross-module dependencies:
 * <ul>
 *   <li>{@code core.events.PartnerStatusChanged} — consumed by
 *       {@code PartnerStatusChangedListener} to reactively update watchlist verdict columns</li>
 *   <li>{@code screening.domain.ScreeningService} — called by
 *       {@link hu.riskguard.screening.domain.WatchlistMonitor} (lives in screening to avoid
 *       circular dependency) for latest snapshot/verdict data</li>
 * </ul>
 * Verdict enrichment uses denormalized columns ({@code last_verdict_status},
 * {@code last_checked_at}) on {@code watchlist_entries}, populated by the
 * {@code PartnerStatusChanged} event listener and the background monitoring cycle.
 */
package hu.riskguard.notification;
