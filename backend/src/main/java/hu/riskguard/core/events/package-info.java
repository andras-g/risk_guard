/**
 * Event catalog — lists all application events used for cross-module communication.
 *
 * <h2>Identity Module Events</h2>
 * <ul>
 *   <li>{@code TenantContextSwitchedEvent} — published when a user switches active tenant context.
 *       Source: {@code hu.riskguard.identity.domain.events}</li>
 * </ul>
 *
 * <h2>Screening Module Events</h2>
 * <ul>
 *   <li>{@code PartnerSearchCompleted} — published after a partner search completes.
 *       Contains snapshotId, verdictId, tenantId. Source: {@code hu.riskguard.screening.domain.events}</li>
 * </ul>
 *
 * <h2>Core (Shared) Events</h2>
 * <ul>
 *   <li>{@link PartnerStatusChanged} — published when a verdict status changes for a partner.
 *       Contains verdictId, tenantId, taxNumber, previousStatus, newStatus, timestamp.
 *       Published by: {@code ScreeningService.search()} (user-initiated) and
 *       {@code WatchlistMonitor} (24h background cycle).
 *       Consumed by: {@code PartnerStatusChangedListener} (notification module).
 *       Moved from {@code screening.domain.events} to {@code core.events} in Story 3.7
 *       to break a circular module dependency.</li>
 * </ul>
 *
 * <h3>PII Policy</h3>
 * <p>Events should minimize PII. Most events carry only UUIDs, enums, and timestamps.
 * Exception: {@link PartnerStatusChanged} includes {@code taxNumber} for watchlist entry
 * lookup — this field must NEVER be logged directly; use {@code PiiUtil.maskTaxNumber()}
 * in all log statements.
 */
package hu.riskguard.core.events;
