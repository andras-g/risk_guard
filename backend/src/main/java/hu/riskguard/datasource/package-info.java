/**
 * Data source module — parallel data acquisition from government registries and APIs
 * with circuit breaker resilience and configurable adapter selection.
 *
 * <p>Module structure:
 * <ul>
 *   <li>{@code api/dto/} — Public DTOs shared with consuming modules (e.g., screening)</li>
 *   <li>{@code domain/} — Public facade ({@code DataSourceService}) and port interface ({@code CompanyDataPort})</li>
 *   <li>{@code internal/} — Aggregator, adapters, configuration — NOT accessible outside this module</li>
 * </ul>
 *
 * <p>Adapter selection is driven by the {@code riskguard.data-source.mode} property:
 * <ul>
 *   <li>{@code demo} — In-memory fixtures via {@code DemoCompanyDataAdapter}</li>
 *   <li>{@code test}/{@code live} — Reserved for NAV Online Számla API adapters (future)</li>
 * </ul>
 */
package hu.riskguard.datasource;
