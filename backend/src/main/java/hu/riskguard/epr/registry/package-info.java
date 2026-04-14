/**
 * EPR Registry sub-module — Product-Packaging Bill-of-Materials CRUD.
 *
 * <p>Manages the per-product packaging registry introduced by CP-5 (Sprint Change Proposal 5)
 * under 80/2023. (III. 14.) Korm. rendelet and pre-wired for PPWR (Regulation 2025/40) fields.
 *
 * <p>Module structure mirrors the parent {@code hu.riskguard.epr} module:
 * <ul>
 *   <li>{@code api/} — REST controller and DTOs (public API surface)</li>
 *   <li>{@code domain/} — service facade and domain records (public domain surface)</li>
 *   <li>{@code internal/} — repository implementations (package-private)</li>
 * </ul>
 *
 * <p>Table ownership: {@code products}, {@code product_packaging_components},
 * {@code registry_entry_audit_log}. No other module may write to
 * {@code product_packaging_components} — enforced by
 * {@code EpicNineInvariantsTest.only_registry_package_writes_to_product_packaging_components}.
 *
 * <p>Tenant isolation: {@code products} and {@code registry_entry_audit_log} carry explicit
 * {@code tenant_id}. {@code product_packaging_components} has NO {@code tenant_id} column —
 * tenant isolation is transitive via {@code product_id → products.tenant_id}. All repository
 * queries MUST join through {@code products} and filter on {@code tenant_id}.
 */
package hu.riskguard.epr.registry;
