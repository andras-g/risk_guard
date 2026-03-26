/**
 * EPR module — Extended Producer Responsibility (KGyfR) fee management.
 *
 * <p>Handles EPR material classification, fee calculation based on Hungarian KGyfR legislation
 * (80/2023 Korm. rendelet), and MOHU export generation. Manages material templates, fee tables,
 * the DAG-based classification wizard, and quarterly filing workflows.
 *
 * <p>Module structure follows the reference implementation ({@code hu.riskguard.screening}):
 * <ul>
 *   <li>{@code api/} — REST controllers and DTOs (public API surface)</li>
 *   <li>{@code domain/} — service facade and application events (public domain surface)</li>
 *   <li>{@code internal/} — repository and internal helpers (package-private)</li>
 * </ul>
 *
 * <p>Cross-module access is restricted to {@code api/} and {@code domain/} packages only.
 * The {@code internal/} package is enforced as inaccessible by Spring Modulith verification
 * and ArchUnit rules.
 *
 * <p>Table ownership: {@code epr_configs}, {@code epr_calculations}, {@code epr_exports},
 * {@code epr_material_templates}. No other module may access these tables.
 */
package hu.riskguard.epr;
