/**
 * Screening module — partner risk screening (search, snapshots, verdicts, audit trail).
 *
 * <p>This is the reference implementation module. All future modules
 * (epr, scraping, notification) must follow the patterns established here:
 * <ul>
 *   <li>{@code api/} — REST controllers and DTOs (public API surface)</li>
 *   <li>{@code domain/} — service facade and application events (public domain surface)</li>
 *   <li>{@code internal/} — repository and internal helpers (package-private)</li>
 * </ul>
 *
 * <p>Cross-module access is restricted to {@code api/} and {@code domain/} packages only.
 * The {@code internal/} package is enforced as inaccessible by Spring Modulith verification.
 */
package hu.riskguard.screening;
