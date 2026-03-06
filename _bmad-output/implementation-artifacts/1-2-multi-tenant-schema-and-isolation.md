# Story 1.2: Multi-tenant Schema & Isolation

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a User,
I want my data to be strictly isolated from other companies at the database and repository layers,
so that I can trust that my sensitive information is never accidentally exposed.

## Acceptance Criteria

1. **Schema Integrity:** Every application table (except core identity/tenant metadata) MUST contain a `tenant_id NOT NULL` column.
2. **Flyway Migration:** Migration `V20260305_001__create_identity_tables.sql` must be verified against Postgres 17.
3. **Context Population:** `TenantFilter` correctly populates the `SecurityContext` and `TenantContext` from the `active_tenant_id` claim in a mock/real JWT.
4. **Query Enforcement:** Any jOOQ query executed by the repository layer MUST include a `tenant_id` filter; tests must verify that cross-tenant data leakage is impossible.
5. **MDC Logging:** The `tenantId` is present in the SLF4J MDC for all request-bound log statements.

## Tasks / Subtasks

- [x] Verify database schema constraints (`tenant_id NOT NULL`) (AC: 1, 2)
- [x] Implement/Refine `TenantFilter` for JWT claim extraction (AC: 3)
- [x] Setup `TenantContext` thread-local holder (AC: 3)
- [x] Configure jOOQ to enforce `tenant_id` filtering (AC: 4)
- [x] Add `TenantIsolationIntegrationTest` using Testcontainers (AC: 4)
- [x] Verify MDC logging in `TenantFilter` (AC: 5)

### Review Follow-ups (AI)

- [x] [AI-Review][CRITICAL] Severe Isolation Bypass in TenantAwareDSLContext (selectFrom only) [backend/src/main/java/hu/riskguard/core/security/TenantAwareDSLContext.java]
- [x] [AI-Review][CRITICAL] Fail-Open Risk on missing tenant_id column in TenantAwareDSLContext [backend/src/main/java/hu/riskguard/core/security/TenantAwareDSLContext.java:18]
- [x] [AI-Review][CRITICAL] Incomplete Record Enforcement in TenantJooqListener (DML bypass) [backend/src/main/java/hu/riskguard/core/security/TenantJooqListener.java:26]
- [x] [AI-Review][MEDIUM] MDC Context loss for asynchronous/background tasks [backend/src/main/java/hu/riskguard/core/security/TenantFilter.java:41]
- [x] [AI-Review][MEDIUM] BaseRepository Lack of Enforcement (selectFromTenant is optional) [backend/src/main/java/hu/riskguard/core/repository/BaseRepository.java:17]
- [x] [AI-Review][LOW] Missing foreign key references to tenants table in guest_sessions [backend/src/main/resources/db/migration/V20260305_001__create_identity_tables.sql:32]
...
### Completion Notes List

- ✅ Verified `tenant_id NOT NULL` constraints in `V20260305_001__create_identity_tables.sql`.
- ✅ Implemented `TenantJooqListener` as a `RecordListener` to automatically set `tenant_id` on `store()`.
- ✅ Implemented `TenantAwareDSLContext` to automatically inject `tenant_id` filters into SELECT and fetchCount queries.
- ✅ Added `AsyncConfig` with `MdcTaskDecorator` to ensure `tenantId` is preserved across thread boundaries.
- ✅ Added Flyway migration `V20260306_001__add_guest_sessions_fk.sql` to establish database-level integrity.
- ✅ Updated `TenantIsolationIntegrationTest` to verify auto-filtering using Testcontainers PostgreSQL 17.
- ✅ Verified MDC logging of `tenantId` in `TenantFilter`.

### File List

- `backend/build.gradle` (Updated dependencies and jOOQ version)
- `backend/src/main/resources/application.yml` (Disabled Microsoft SSO placeholder)
- `backend/src/main/resources/db/migration/V20260305_002__create_modulith_tables.sql` (New migration for Modulith persistence)
- `backend/src/main/resources/db/migration/V20260306_001__add_guest_sessions_fk.sql` (New FK for guests)
- `backend/src/main/java/hu/riskguard/core/security/TenantJooqListener.java` (Updated listener for DML guards)
- `backend/src/main/java/hu/riskguard/core/security/TenantAwareDSLContext.java` (Robust DSLContext wrapper)
- `backend/src/main/java/hu/riskguard/core/config/AsyncConfig.java` (MDC propagation)
- `backend/src/main/java/hu/riskguard/core/config/JooqConfig.java` (New config for jOOQ)
- `backend/src/main/java/hu/riskguard/core/repository/BaseRepository.java` (Base class for repositories)
- `backend/src/test/java/hu/riskguard/identity/TenantIsolationIntegrationTest.java` (Updated integration test)

## Change Log

- 2026-03-06: Ultimate context engine analysis completed - comprehensive developer guide created.
- 2026-03-06: Multi-tenant isolation implemented and verified at the database and repository layers.
- 2026-03-06: Hardened isolation guards and fixed review findings related to bypasses and context loss.

## Story Completion Status

Status: done
Completion Note: Multi-tenant isolation fully implemented via jOOQ listeners and hardened DSLContext wrapper. Resolved all review findings including critical isolation bypasses and MDC context loss in async tasks.




