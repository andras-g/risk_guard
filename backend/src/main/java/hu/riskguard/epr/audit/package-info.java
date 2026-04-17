/**
 * EPR Audit sub-module — centralized audit-row writes and reads.
 *
 * <p>Per ADR-0003, {@link hu.riskguard.epr.audit.AuditService} is the sole permitted
 * access path to audit tables from anywhere outside this package. Repositories live
 * in {@code internal/} and are reached only through the service facade.
 *
 * <p>Table ownership: {@code registry_entry_audit_log}. Future tables
 * ({@code aggregation_audit}, added in Story 10.8) will also be owned here.
 *
 * <p>Enforcement: {@code EpicTenInvariantsTest.only_audit_package_writes_to_audit_tables}
 * and {@code audit_service_is_the_facade}.
 *
 * <p><b>Transactional contract:</b> {@code AuditService} MUST NOT carry {@code @Transactional}.
 * It inherits the caller's transaction so audit writes commit atomically with the mutation
 * that prompted them — a compliance requirement for Epic 10 filing submissions.
 */
@org.springframework.modulith.NamedInterface("audit")
package hu.riskguard.epr.audit;
