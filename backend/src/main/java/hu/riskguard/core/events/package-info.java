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
 *   <li>{@code PartnerStatusChanged} — (placeholder for Story 2.3+) published when a verdict status changes.
 *       Source: {@code hu.riskguard.screening.domain.events}</li>
 * </ul>
 *
 * <h3>PII Policy</h3>
 * <p>No event record may contain PII (email, tax number, personal name).
 * Events carry only UUIDs, enums, and timestamps. Resolve PII via module facades if needed.
 */
package hu.riskguard.core.events;
