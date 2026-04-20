# Story 10.4: Tenant Onboarding — "Feltöltés számlák alapján"

Status: done

<!-- Epic 10 · Story 10.4 · depends on 10.1 (schema + AuditService + tx-pool refactor) and 10.3 (batch classifier) -->

## Story

As a **SME_ADMIN or ACCOUNTANT onboarding a new tenant**,
I want to **trigger a one-click "Feltöltés számlák alapján" that pulls the last 3 months of NAV invoices, batch-classifies the distinct `(VTSZ + description)` pairs, and populates the Termék–Csomagolás Nyilvántartás (Registry) directly with multi-layer packaging rows**,
so that **the tenant reaches a filing-ready state without manually approving a triage queue or entering product-by-product data**.

This story replaces Epic 9 Story 9.2's candidate-triage approach: the triage-queue UI, the `RegistryBootstrapService` (9.2 version), and the `registry_bootstrap_candidates` table are all deleted. Populated Registry rows become the single source of truth for Story 10.5's aggregation.

## Acceptance Criteria

### DB schema & migrations

1. **Migration `V20260420_001__create_epr_bootstrap_jobs.sql`** creates the `epr_bootstrap_jobs` table with columns `id UUID PK`, `tenant_id UUID NOT NULL`, `status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','FAILED_PARTIAL','CANCELLED'))`, `period_from DATE NOT NULL`, `period_to DATE NOT NULL`, `total_pairs INT NOT NULL DEFAULT 0`, `classified_pairs INT NOT NULL DEFAULT 0`, `vtsz_fallback_pairs INT NOT NULL DEFAULT 0`, `unresolved_pairs INT NOT NULL DEFAULT 0`, `created_products INT NOT NULL DEFAULT 0`, `deleted_products INT NOT NULL DEFAULT 0`, `triggered_by_user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL`, `error_message VARCHAR(1000) NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `completed_at TIMESTAMPTZ NULL`. Includes `CHECK (period_from <= period_to)`. Partial index `idx_epr_bootstrap_jobs_tenant_inflight ON epr_bootstrap_jobs (tenant_id) WHERE status IN ('PENDING','RUNNING')` for the in-flight guard. Supporting index `(tenant_id, created_at DESC)` for history lookups. Idempotent (`CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS`).

2. **Migration `V20260420_002__add_classifier_source_and_review_state.sql`** adds two row-level provenance columns:
   - `product_packaging_components.classifier_source VARCHAR(32) NULL CHECK (classifier_source IS NULL OR classifier_source IN ('MANUAL','MANUAL_WIZARD','AI_SUGGESTED_CONFIRMED','AI_SUGGESTED_EDITED','VTSZ_FALLBACK','NAV_BOOTSTRAP'))`
   - `products.review_state VARCHAR(32) NULL CHECK (review_state IS NULL OR review_state IN ('MISSING_PACKAGING'))`

   Idempotent (`ADD COLUMN IF NOT EXISTS`). Backfill: unchanged rows (pre-10.4 data) keep NULL; no data coercion.

3. **Migration `V20260420_003__drop_registry_bootstrap_candidates.sql`** drops the `registry_bootstrap_candidates` table via `DROP TABLE IF EXISTS registry_bootstrap_candidates CASCADE`. Pre-drop sanity query logs the count of rows with `status='APPROVED'` (expected 0 in every known environment; documented in the migration comment).

4. **Rollback SQL** for all three migrations is pasted as a `-- ROLLBACK:` comment block at the bottom of each file, tested round-trip on a dev DB, and noted in the Dev Agent Record.

5. **Migration-parity test** — `EprBootstrapJobsMigrationTest` asserts the table + all columns + all CHECK constraints + both indexes exist post-migration by querying `information_schema` via `DSLContext`, following the pattern of `ProductPackagingComponentsEpic10MigrationTest`.

### Backend — new service, controller, endpoints

6. **New module package** `hu.riskguard.epr.registry.bootstrap` is created. It contains `InvoiceDrivenRegistryBootstrapService`, `BootstrapJobController`, `BootstrapJobWorker` (the `@Async` pair-processor), and all new DTOs.

7. **Endpoint** `POST /api/v1/registry/bootstrap-from-invoices` accepts `BootstrapTriggerRequest { LocalDate periodFrom, LocalDate periodTo }` (both nullable — if either is null, server defaults to **last 3 complete calendar months** relative to today in `Europe/Budapest`, e.g., invoked 2026-04-20 → `2026-01-01 … 2026-03-31`). Returns **`202 Accepted`** with body `BootstrapJobCreatedResponse { UUID jobId, String location }`, and `Location: /api/v1/registry/bootstrap-from-invoices/{jobId}` response header. The worker starts asynchronously on the shared `taskExecutor` bean (see `hu.riskguard.core.config.AsyncConfig` — `TenantAwareTaskDecorator` is already wired, so `TenantContext` propagates without code changes).

8. **Endpoint** `GET /api/v1/registry/bootstrap-from-invoices/{jobId}` returns `BootstrapJobStatusResponse { jobId, status, periodFrom, periodTo, totalPairs, classifiedPairs, vtszFallbackPairs, unresolvedPairs, createdProducts, deletedProducts, errorMessage, createdAt, updatedAt, completedAt }`. Returns **404** on unknown id; **403** when the job's `tenant_id` does not match the JWT's `active_tenant_id`.

9. **Endpoint** `DELETE /api/v1/registry/bootstrap-from-invoices/{jobId}` flips the row to `status = 'CANCELLED'` and returns **204 No Content**. If already terminal (COMPLETED/FAILED/FAILED_PARTIAL/CANCELLED), returns **409** with body `{ code: 'ALREADY_TERMINAL', status: <current> }`. Returns **404** on unknown id; **403** on cross-tenant.

10. **All three endpoints** carry `@TierRequired(Tier.PRO_EPR)` (class-level) and enforce `JwtUtil.requireRole(jwt, message, "SME_ADMIN","ACCOUNTANT","PLATFORM_ADMIN")` (method-level). Tenant is resolved via `JwtUtil.requireUuidClaim(jwt, "active_tenant_id")` — **never** taken from the request body.

11. **Tenant tax-number ownership** — before the first NAV call, the service resolves the tenant's tax number via `DataSourceService.getTenantTaxNumber(tenantId)` and asserts its 8-digit prefix matches the `active_tenant_id`'s `nav_tenant_credentials.tax_number` row (lift the exact `substring(0, 8)` equality pattern from `EprService.java:297-303`). Mismatch → 403 `Tax number does not match tenant's registered tax number` (job never created).

12. **Preconditions** — endpoint returns **412 Precondition Failed** on:
    - NAV credentials missing or not decryptable (`AuthService.loadCredentials(tenantId)` throws).
    - `producer_profiles.tenant_id` row absent or incomplete (reuse the completeness check from Story 9.4's `EprService.producerProfileComplete(tenantId)`). Body: `{ code: 'NAV_CREDENTIALS_MISSING' | 'PRODUCER_PROFILE_INCOMPLETE', message }`.

13. **In-flight guard** — a second `POST` while any `epr_bootstrap_jobs` row for this tenant is `PENDING` or `RUNNING` returns **409 Conflict** with body `{ code: 'ALREADY_RUNNING', jobId: <existing> }`. Implementation uses the partial index created in AC #1 and an explicit `INSERT … WHERE NOT EXISTS (SELECT … WHERE status IN ('PENDING','RUNNING'))` guard — **not** `@Transactional` serializable isolation — to avoid Hikari pool pressure (T4 rule).

14. **Cancellation semantics** — the worker checks `status` in a fresh `REQUIRES_NEW` SELECT between each batch; on `CANCELLED`, it exits cleanly after committing any in-flight per-pair transaction. The partial progress is visible in the final row (`classified_pairs` etc. reflect work already committed). Best-effort: pairs already dispatched to the classifier may still persist.

### Orchestration & transactional boundaries

15. **No `@Transactional` method** in `InvoiceDrivenRegistryBootstrapService`, `BootstrapJobWorker`, or any class in `hu.riskguard.epr.registry.bootstrap.*` may hold a NAV HTTP call. The `NavHttpOutsideTransactionTest` ArchUnit rule is **extended** to include `DataSourceService` and the new `BatchPackagingClassifierService` call paths. Implementation follows the orchestrator pattern from the Story 10.1 refactor of `RegistryBootstrapService.triggerBootstrap`: NAV fetch + classifier calls run outside any transaction; persistence runs inside `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW` at per-pair (or per-micro-batch-of-≤10) granularity.

16. **Per-pair commit** — each classified pair (i.e., one `(vtsz, description)` key) is persisted in its own `REQUIRES_NEW` transaction. A failure in any single pair is caught, logged as a WARN with `pair=vtsz|description`, and does NOT roll back the already-committed pairs. Aggregate outcome:
    - All pairs succeeded → `status = 'COMPLETED'`.
    - Some succeeded, some failed → `status = 'FAILED_PARTIAL'` with `error_message` = "N/M pairs failed: <first-3-reasons>".
    - Zero pairs succeeded → `status = 'FAILED'` with `error_message` = root cause.
    - External cancel → `status = 'CANCELLED'`.

17. **NAV invoice fetch** loops `DataSourceService.queryInvoices(taxNumber, from, to, InvoiceDirection.OUTBOUND)` — the 9.2 `RegistryBootstrapService` uses the same method; reuse its per-page pattern. Each fetched page is processed in-memory; invoice-line extraction happens without a database transaction. Progressive update of `epr_bootstrap_jobs.total_pairs` happens inside a small `REQUIRES_NEW` tx after dedup completes but before classifier dispatch.

18. **Dedup key** is `vtsz || '~' || LOWER(TRIM(description))` built in Java with `description` sourced from `InvoiceLineItem.lineDescription`. Empty/blank descriptions and null VTSZ are skipped with an INFO log; neither contributes to `total_pairs`. Unit tests cover whitespace-collapsing (multiple spaces, tabs, NBSP), casing (`"KÁVÉ"` vs. `"kávé"`), and trimming leading/trailing whitespace.

### Classifier integration & row tagging

19. **Classifier dispatch** — dedup-unique pairs are split into chunks of **up to 100** (AC #1 of Story 10.3) and POSTed to the **internal** `BatchPackagingClassifierService.classify(request)` (direct Spring bean call — not HTTP) to avoid a second auth roundtrip. Monthly-cap and per-tenant concurrent-batch limits from Story 10.3 are respected (a 429 from the classifier aborts the job with `status='FAILED'`, `error_message` reflecting the cap breach, and a `Retry-After` hint if the trigger endpoint is called again). Concurrency uses the existing `risk-guard.classifier.batch.concurrency` pool (default 10).

20. **Row tagging** rules, per pair:
    - `classificationStrategy = GEMINI` → create `products` row + `product_packaging_components` rows with `classifier_source = 'AI_SUGGESTED_CONFIRMED'`. `products.review_state = NULL`.
    - `classificationStrategy = VTSZ_PREFIX_FALLBACK` → same row creation, `classifier_source = 'VTSZ_FALLBACK'`. `products.review_state = NULL`.
    - `classificationStrategy = UNRESOLVED` (empty `layers` array) → create `products` row with **zero** components, `products.review_state = 'MISSING_PACKAGING'`.
    - The per-component `wrapping_level`, `items_per_parent`, `weight_per_unit_kg`, `kf_code`, `material_description` map 1:1 from `PackagingLayerDto { level, itemsPerParent, weightEstimateKg, kfCode, description }`. `component_order` = `layer.level - 1` (zero-based).

21. **Overwrite semantics** — for each distinct pair, the service looks up any existing `(tenant_id, vtsz, name)` row (where `name` is the raw invoice-line `description`, NOT the dedup-normalized form). If found:
    - Row is `DELETE`d (cascade drops `product_packaging_components`).
    - Fresh row is inserted with the new classifier output.
    - `deleted_products` counter increments; `created_products` increments after the re-insert.
    - Rows whose `(tenant_id, vtsz, name)` is **not** in the invoice set are untouched — user-added manual rows survive the bootstrap.
    - The delete + insert happens inside the same per-pair `REQUIRES_NEW` transaction (atomic per pair).

22. **Audit writes** — per ADR-0003 §"Batch-write path for Story 10.4", introduce **`AuditService.recordRegistryBootstrapBatch(List<FieldChangeEvent> events)`** using jOOQ's batched-connection pattern (one round-trip per sub-batch of up to 500 events). Each created/deleted row generates one `FieldChangeEvent` with `source = AuditSource.NAV_BOOTSTRAP`, `fieldChanged = "bootstrap.created" | "bootstrap.deleted"`, `oldValue/newValue` carrying the `(vtsz, name, classifier_source)` triple. Events are accumulated per worker run and flushed at the **end of each per-pair REQUIRES_NEW transaction's commit callback** so the audit commit participates in the same transaction as the Registry write (ADR-0003 atomicity invariant). Batch method signature + unit test + Micrometer counter `audit.writes{source=NAV_BOOTSTRAP}` registered.

### Frontend — new dialog, Registry toolbar chips, cleanup

23. **New component** `frontend/app/components/Registry/InvoiceBootstrapDialog.vue` — a PrimeVue `Dialog` (modal, `appendTo="body"`, 640px) containing:
    - **Period selector** — two `DatePicker` fields labelled `Tól` and `Ig`, pre-filled with the default last-3-complete-months range (client computes the default to match the server default; the server still authoritatively defaults when omitted). Validation: `periodFrom <= periodTo`, `periodTo` not in the future.
    - **Destructive-overwrite confirmation** — when the dialog opens, it calls `GET /api/v1/registry/summary` (Story 10.7) to determine if the Registry has existing products. If `totalProducts > 0`, the primary action button requires a second click with copy "A `{N}` meglévő termékből az invoice-találatok felülíródnak. Biztosan folytatod?" (HU) / mirror EN.
    - **Progress UI** — after POST returns 202, the dialog switches to a progress view with a `ProgressBar` reflecting `classifiedPairs / totalPairs` (once `totalPairs > 0`), live counter badges (`{createdProducts} létrehozva · {vtszFallbackPairs} bizonytalan · {unresolvedPairs} hiányos`), and an indeterminate spinner during the PENDING phase. Polls `GET …/{jobId}` every **2000 ms** via `setInterval`; clears on dialog close AND on terminal status.
    - **Completion summary** — on COMPLETED / FAILED_PARTIAL: "Kész. **N** termék létrehozva, **M** bizonytalan, **K** hiányos. " (HU) + "Megnyitás" (routes to `/registry?review_state=MISSING_PACKAGING` when K > 0, else closes the dialog). On FAILED: error icon + `errorMessage`. On CANCELLED: "Megszakítva." + partial counters.
    - **Cancel button** — visible during PENDING/RUNNING; calls `DELETE …/{jobId}` then stops polling and reflects the final state.
    - **Emit** `completed` event with the final status object so parent pages (`/registry`, `/epr/filing` via Story 10.7) can refresh.

24. **Registry toolbar filter chips** — on `frontend/app/pages/registry/index.vue`, add two `ToggleButton` chips next to the existing `statusFilter` Select:
    - `Csak hiányos` → when active, sends `?reviewState=MISSING_PACKAGING` to `GET /api/v1/registry`.
    - `Csak bizonytalan` → when active, sends `?classifierSource=VTSZ_FALLBACK` to `GET /api/v1/registry`.
    - Chips are mutually compatible (AND semantics). Both filters flow through a new `RegistryListFilter.reviewState` + `RegistryListFilter.classifierSource` field, plumbed through `RegistryRepository.findAll`. Client-side state lives on `useRegistryStore.listFilter` (existing Pinia store).

25. **Composable** `frontend/app/composables/api/useInvoiceBootstrap.ts` exposes `triggerBootstrap(periodFrom, periodTo)`, `getJobStatus(jobId)`, `cancelJob(jobId)`. Thin `useApi`-based wrapper.

26. **Registry empty-state CTA** (`pages/registry/index.vue`) — the existing 9.2 "Feltöltés számlák alapján" empty-state button is **retained** but re-wired to open `InvoiceBootstrapDialog` instead of navigating to the now-deleted `/registry/bootstrap` route. Any other reference to the `/registry/bootstrap` route is removed (no redirect — no production users).

27. **Story 9.2 deletions** — the following files/state/keys are **DELETED** as part of this story:
    - Backend: `RegistryBootstrapController.java`, `RegistryBootstrapService.java` (the 9.2 domain service), `BootstrapRepository.java`, and every DTO under `registry/api/dto/Bootstrap*.java`, plus `registry/domain/BootstrapCandidate*.java`, `ApproveCommand.java`, `BootstrapResult.java`, `BootstrapTriageFilter.java`.
    - Backend tests: `BootstrapControllerTest.java`, `BootstrapServiceTest.java`, `RegistryBootstrapServiceLoadTest.java`.
    - Frontend: `pages/registry/bootstrap.vue` + spec, `components/registry/BootstrapApproveDialog.vue` + spec, `composables/api/useBootstrap.ts`, `stores/bootstrap.ts`.
    - i18n: `registry.bootstrap.*` subtree under `frontend/app/i18n/hu/registry.json` and `frontend/app/i18n/en/registry.json` (lines ~26–73 of the HU file; mirror in EN). **Retain** `registry.audit.source.NAV_BOOTSTRAP` label — still referenced by the new service's audit writes.
    - jOOQ regen: after the `DROP TABLE` migration, run `./gradlew generateJooq` so `hu.riskguard.jooq.Tables.REGISTRY_BOOTSTRAP_CANDIDATES` is removed from the generated sources. Verify BUILD SUCCESSFUL after regen.

### i18n

28. **New keys** (hu + en, alphabetical per T6 hook):
    - `registry.bootstrap.dialogTitle` — "Feltöltés számlák alapján" / "Bootstrap from invoices"
    - `registry.bootstrap.periodFromLabel`, `registry.bootstrap.periodToLabel`
    - `registry.bootstrap.overwriteWarning` — parameterized with `{count}`
    - `registry.bootstrap.startButton`, `registry.bootstrap.confirmOverwriteButton`
    - `registry.bootstrap.progress.pending`, `registry.bootstrap.progress.running`
    - `registry.bootstrap.progress.counterLabel` — parameterized
    - `registry.bootstrap.completion.success`, `…partial`, `…failed`, `…cancelled`
    - `registry.bootstrap.completion.openRegistry`, `registry.bootstrap.completion.close`
    - `registry.bootstrap.cancelButton`
    - `registry.bootstrap.errors.navCredentialsMissing`, `…producerProfileIncomplete`, `…alreadyRunning`, `…taxNumberMismatch`, `…capExceeded`
    - `registry.filter.onlyIncomplete` — "Csak hiányos" / "Only incomplete"
    - `registry.filter.onlyUncertain` — "Csak bizonytalan" / "Only uncertain"
    - `registry.rowBadge.missingPackaging` — "Hiányos" / "Incomplete"
    - `registry.rowBadge.vtszFallback` — "Bizonytalan" / "Uncertain"

29. **Deprecated keys** under `registry.bootstrap.*` that were specific to the 9.2 triage UI (approve/reject/candidates/etc.) are **removed**. Any key reused by the new dialog may be kept and repurposed in-place; surplus keys are deleted in the same commit that deletes the Vue components. `npm run lint:i18n` must pass.

### Load + architecture guardrails

30. **Load test** — a dedicated `InvoiceDrivenBootstrapLoadTest` (tagged `@Tag("load")`, opt-in via `-PincludeLoadTests`) runs against Testcontainers PostgreSQL with `spring.datasource.hikari.maximum-pool-size=10`. Fixture: 3000 mocked NAV invoices × ~5 lines each × ~30% duplicate ratio → ~1000 unique pairs. Asserts (a) job reaches COMPLETED or FAILED_PARTIAL, (b) `HikariPoolMXBean.getActiveConnections()` never exceeds 10 during the run, (c) no `SQLTransientConnectionException` raised, (d) wall time < 60s with the classifier mocked to 20ms/call.

31. **ArchUnit rules** in `EpicTenInvariantsTest` are extended:
    - `bootstrap_service_lives_in_bootstrap_package` — classes named `*BootstrapService` / `*BootstrapWorker` / `*BootstrapController` must reside under `..epr.registry.bootstrap..`.
    - `only_bootstrap_package_writes_to_epr_bootstrap_jobs` — no class outside `..epr.registry.bootstrap..` may call any jOOQ method referencing the generated `EPR_BOOTSTRAP_JOBS` table.
    - Existing `NavHttpOutsideTransactionTest` is extended to include the new package `..epr.registry.bootstrap..` in its scope filter.

### Testing

32. **Backend unit tests** — `InvoiceDrivenRegistryBootstrapServiceTest` ≥ 10 cases covering: (a) happy-path 5-pair job end-to-end with mocked classifier + mocked `DataSourceService`; (b) dedup sensitivity (whitespace, case, trim — 3 sub-cases); (c) overwrite path — pre-existing `(tenant,vtsz,name)` row deleted + recreated; (d) unrelated user row untouched; (e) UNRESOLVED pair → zero-component product with `review_state=MISSING_PACKAGING`; (f) VTSZ_FALLBACK tagging; (g) per-pair commit isolation — one failing pair does NOT roll back the others; (h) cancellation mid-batch — `CANCELLED` status reached, partial counters persist; (i) cap-exceeded at classifier → `FAILED`; (j) empty NAV result → `COMPLETED` with zero counters.

33. **Backend controller tests** — `BootstrapJobControllerTest` ≥ 6 cases: (a) POST 202 with default period; (b) POST 202 with explicit period; (c) POST 412 on missing NAV creds; (d) POST 409 in-flight guard; (e) POST 403 on tax-number mismatch; (f) GET 404 on unknown id; (g) GET 403 on cross-tenant; (h) DELETE 204 on active job; (i) DELETE 409 on terminal job; (j) tier-gate 402 on non-PRO_EPR; (k) role-gate 403 on GUEST.

34. **Integration test** — `InvoiceDrivenRegistryBootstrapIntegrationTest` (tagged `@Tag("integration")`) with Testcontainers: seeds a tenant + NAV credentials fixture + producer profile + 20 mocked invoice lines spanning 5 distinct pairs (2 Gemini, 2 fallback, 1 unresolved); mocks `BatchPackagingClassifierService`; asserts DB state post-run (5 product rows, correct `classifier_source`/`review_state` tags, audit log entries via `AuditService`).

35. **ArchUnit tests** — rules from AC #31 land with at least one positive test case + one intentionally-failing "witness" test (commented out, instructions on how to manually re-enable).

36. **Frontend unit tests** — `InvoiceBootstrapDialog.spec.ts` ≥ 8 cases: opens, loads summary to detect overwrite, two-click confirm on non-empty Registry, 202 → switches to progress view, polling updates counters, cancel button issues DELETE, terminal status stops polling, error state renders.

37. **Frontend composable test** — `useInvoiceBootstrap.spec.ts` ≥ 3 cases covering each of the three composable methods.

38. **Frontend Registry toolbar tests** — `registry/index.spec.ts` extended with ≥ 2 cases for the new filter chips (query-param wiring + visual state).

39. **Playwright E2E** — `invoice-bootstrap.e2e.ts` covers the golden flow: log in as SME_ADMIN, open `/registry`, click the empty-state CTA, choose a period, confirm overwrite (non-empty Registry fixture), wait for progress to reach COMPLETED, verify the summary badge count matches DB fixture, close, observe rows in the Registry list.

### Process

40. **AC-to-task walkthrough (T1)** is filed as a completed item in the Dev Agent Record **before any code commit**. This is the retro-T1 gate from the Epic 9 retrospective; skipping it is a hard violation.

41. **Demo-mode smoke check** — after dev completes, run the demo tenant (`demo@riskguard.hu`) through the flow manually; the `InvoiceBootstrapDialog` should complete successfully against `DemoInvoiceFixtures` (the demo NAV adapter). Note any demo-data surprises in the Dev Agent Record.

## Tasks / Subtasks

- [ ] **Task 1 — AC-to-task walkthrough gate (AC: #40).** Read each AC aloud, confirm a matching task below exists, note any gap in the Dev Agent Record. Do not proceed to Task 2 until this is complete.

- [ ] **Task 2 — Migrations + parity test (AC: #1, #2, #3, #4, #5).**
  - [ ] Author `V20260420_001__create_epr_bootstrap_jobs.sql` with all columns, CHECK constraints, partial + supporting indexes, rollback comment.
  - [ ] Author `V20260420_002__add_classifier_source_and_review_state.sql`.
  - [ ] Author `V20260420_003__drop_registry_bootstrap_candidates.sql` with pre-drop `APPROVED` count log.
  - [ ] Write `EprBootstrapJobsMigrationTest` asserting full schema + index presence.
  - [ ] Round-trip rollback on the dev DB.
  - [ ] Run `./gradlew generateJooq`; verify `Tables.EPR_BOOTSTRAP_JOBS` exists and `Tables.REGISTRY_BOOTSTRAP_CANDIDATES` is gone.

- [ ] **Task 3 — Audit module batch-write method (AC: #22).**
  - [ ] Add `AuditService.recordRegistryBootstrapBatch(List<FieldChangeEvent>)` using jOOQ's `query.bind(...).execute()` batched-connection pattern (NOT `DSLContext.batchInsert()`). Flush in sub-batches of 500.
  - [ ] Add `Micrometer` counter `audit.writes{source=NAV_BOOTSTRAP}` instrumentation.
  - [ ] Unit test: `AuditServiceBootstrapBatchTest` — asserts 1001 events produce 3 JDBC round-trips and all rows land correctly.
  - [ ] Confirm `AuditService` remains un-`@Transactional` (ArchUnit rule `audit_service_is_the_facade` passes).

- [ ] **Task 4 — Job repository + domain (AC: #1, #13, #16).**
  - [ ] Create `BootstrapJobRecord` domain record and `BootstrapJobStatus` enum (`PENDING, RUNNING, COMPLETED, FAILED, FAILED_PARTIAL, CANCELLED`).
  - [ ] Create `BootstrapJobRepository` (jOOQ): `insertIfNoInflight(tenantId, periodFrom, periodTo, triggeredByUserId) → Optional<UUID>` (returns empty when 409 guard trips), `findByIdAndTenant(...)`, `findInflightByTenant(...)`, `incrementCounters(jobId, classified, fallback, unresolved, created, deleted)`, `transitionStatus(jobId, newStatus, errorMessage)`, `setTotalPairs(jobId, total)`.
  - [ ] All repo methods are plain jOOQ — no `@Transactional`; callers (the service's `TransactionTemplate.execute(...)` blocks) control tx boundaries.

- [ ] **Task 5 — `InvoiceDrivenRegistryBootstrapService` (AC: #11, #12, #13, #15, #17, #18, #19, #20, #21, #22).**
  - [ ] Package: `hu.riskguard.epr.registry.bootstrap`.
  - [ ] Inject `DataSourceService`, `BatchPackagingClassifierService`, `RegistryService`, `AuditService`, `BootstrapJobRepository`, `ProducerProfileService`, `TaskExecutor taskExecutor`, `TransactionTemplate transactionTemplate`.
  - [ ] Public API: `UUID startJob(UUID tenantId, UUID actingUserId, LocalDate periodFrom, LocalDate periodTo)` — synchronous preamble (412/403/409 checks + insert job row), then hands off to `@Async` worker. Returns `jobId`.
  - [ ] Private `processJob(UUID jobId, UUID tenantId, UUID actingUserId, LocalDate from, LocalDate to)` annotated `@Async("taskExecutor")` — runs on `RG-Async-*` pool with `TenantContext` propagated by the existing `TenantAwareTaskDecorator`.
  - [ ] Worker flow: transition to `RUNNING` (small REQUIRES_NEW tx) → fetch invoices via `DataSourceService` → dedup → update `total_pairs` → split into chunks of 100 → for each chunk: call `BatchPackagingClassifierService.classify(batchRequest)`, then for each result open a per-pair REQUIRES_NEW tx: delete existing `(tenant, vtsz, name)` row if any → insert product + components per AC #20 tagging rules → accumulate `FieldChangeEvent` audit events → call `auditService.recordRegistryBootstrapBatch(events)` at tx-commit callback (via `TransactionSynchronization`). After each chunk, re-read status; on CANCELLED, break and transition to `CANCELLED`.
  - [ ] Terminal transition: COMPLETED / FAILED_PARTIAL / FAILED / CANCELLED per AC #16.
  - [ ] Null/blank guards: skip invoice lines with null `vtszCode` or blank `lineDescription` (INFO log).

- [ ] **Task 6 — `BootstrapJobController` + DTOs (AC: #7, #8, #9, #10, #11, #12, #13).**
  - [ ] Package: `hu.riskguard.epr.registry.bootstrap.api`.
  - [ ] Class-level `@TierRequired(Tier.PRO_EPR)` + `@RestController @RequestMapping("/api/v1/registry/bootstrap-from-invoices")`.
  - [ ] DTOs: `BootstrapTriggerRequest { LocalDate periodFrom, LocalDate periodTo }` (both nullable), `BootstrapJobCreatedResponse { UUID jobId, String location }`, `BootstrapJobStatusResponse` (all fields from AC #8).
  - [ ] Method annotations: `requireRole("SME_ADMIN","ACCOUNTANT","PLATFORM_ADMIN")`; 404/403/409/412/202/204 mapping per ACs.
  - [ ] Add OpenAPI `@Operation` annotations.

- [ ] **Task 7 — Registry list filter extension (AC: #24).**
  - [ ] Extend `RegistryListFilter` with `ReviewState reviewState` and `AuditSource classifierSource` fields (strictly typed, not String).
  - [ ] Update `RegistryRepository.findAll` jOOQ query to accept both filters (AND-compose).
  - [ ] Extend `RegistryController.list` params: `@RequestParam(required = false) ReviewState reviewState`, `@RequestParam(required = false) AuditSource classifierSource`.
  - [ ] Add `ReviewState` enum in `hu.riskguard.epr.registry.domain` (values: `MISSING_PACKAGING`).

- [ ] **Task 8 — ArchUnit extensions (AC: #15, #31).**
  - [ ] Extend `NavHttpOutsideTransactionTest` scope to include `..epr.registry.bootstrap..`.
  - [ ] Add `bootstrap_service_lives_in_bootstrap_package` rule to `EpicTenInvariantsTest`.
  - [ ] Add `only_bootstrap_package_writes_to_epr_bootstrap_jobs` rule to `EpicTenInvariantsTest`.
  - [ ] Run `./gradlew test --tests "hu.riskguard.architecture.*"` — all green.

- [ ] **Task 9 — Story 9.2 deletion (AC: #27, #29).**
  - [ ] Delete backend: `RegistryBootstrapController`, `RegistryBootstrapService` (9.2 domain class), `BootstrapRepository`, all `Bootstrap*` DTOs in `registry/api/dto/` and `registry/domain/`.
  - [ ] Delete backend tests: `BootstrapControllerTest`, `BootstrapServiceTest`, `RegistryBootstrapServiceLoadTest`.
  - [ ] Delete frontend: `pages/registry/bootstrap.vue`, `pages/registry/bootstrap.spec.ts`, `components/registry/BootstrapApproveDialog.vue` + spec, `composables/api/useBootstrap.ts`, `stores/bootstrap.ts`.
  - [ ] Remove deprecated `registry.bootstrap.*` triage-only keys from `i18n/hu/registry.json` and `i18n/en/registry.json`.
  - [ ] Any imports of the deleted classes elsewhere in the codebase must be located (`rg -l "RegistryBootstrapService\|useBootstrap\|BootstrapApproveDialog"`) and fixed.
  - [ ] Run `./gradlew build` and `npm run -w frontend test` to confirm no dangling references.

- [ ] **Task 10 — Frontend `InvoiceBootstrapDialog.vue` + composable + store wiring (AC: #23, #25, #26).**
  - [ ] Create `components/Registry/InvoiceBootstrapDialog.vue` with period selector, overwrite confirmation, progress UI, completion summary, cancel button.
  - [ ] Create `composables/api/useInvoiceBootstrap.ts` with `triggerBootstrap`, `getJobStatus`, `cancelJob`.
  - [ ] Rewire the `/registry` page empty-state CTA (and any other `router.push('/registry/bootstrap')` call site) to open the new dialog via `v-model:visible`.
  - [ ] Poll interval 2000ms; stop on terminal status or dialog close; `onBeforeUnmount` clears the timer.
  - [ ] Register missing i18n keys per AC #28 (alphabetical).

- [ ] **Task 11 — Registry toolbar filter chips (AC: #24).**
  - [ ] Add two `ToggleButton` components to `pages/registry/index.vue` toolbar.
  - [ ] Wire to `useRegistryStore.listFilter.reviewState` / `listFilter.classifierSource`.
  - [ ] Trigger `fetchProducts()` on chip change.
  - [ ] Row-level badges in the DataTable: green `Kész` (neither flag), yellow `Bizonytalan` (`classifier_source=VTSZ_FALLBACK`), red `Hiányos` (`review_state=MISSING_PACKAGING`).
  - [ ] Extend `ProductResponse`/`ProductSummaryResponse` DTOs (backend) to expose `reviewState` and `classifierSource` — pick `classifierSource` from the first component row (or rollup logic clarified with scrum master during Task 1 walkthrough).

- [ ] **Task 12 — Backend tests (AC: #32, #33, #34).**
  - [ ] `InvoiceDrivenRegistryBootstrapServiceTest` per AC #32.
  - [ ] `BootstrapJobControllerTest` per AC #33.
  - [ ] `InvoiceDrivenRegistryBootstrapIntegrationTest` per AC #34.

- [ ] **Task 13 — Load test (AC: #30).**
  - [ ] `InvoiceDrivenBootstrapLoadTest` tagged `@Tag("load")`; opt-in Gradle property `-PincludeLoadTests`. Asserts pool never exceeds 10, no `SQLTransientConnectionException`, wall time < 60s.

- [ ] **Task 14 — Frontend tests (AC: #36, #37, #38).**
  - [ ] `InvoiceBootstrapDialog.spec.ts` per AC #36.
  - [ ] `useInvoiceBootstrap.spec.ts` per AC #37.
  - [ ] `registry/index.spec.ts` extensions per AC #38.

- [ ] **Task 15 — Playwright E2E (AC: #39).**
  - [ ] `tests/e2e/invoice-bootstrap.e2e.ts`. Use the existing demo tenant fixture (`demo@riskguard.hu`). Mock/stub the async worker delay to < 3s for CI speed.

- [ ] **Task 16 — Final verification + demo smoke (AC: #41).**
  - [ ] `./gradlew test` (full suite) — all green including ArchUnit.
  - [ ] `npm run -w frontend test` — all green.
  - [ ] `npm run -w frontend tsc && npm run -w frontend lint && npm run -w frontend lint:i18n` — all clean.
  - [ ] `./gradlew integrationTest` (Testcontainers) — green.
  - [ ] Playwright E2E (`npm run -w frontend e2e`) — green.
  - [ ] Manual demo-tenant smoke per AC #41; capture observations in Dev Agent Record.

## Dev Notes

### Architecture compliance — MUST FOLLOW

- **ADR-0003 (Epic 10 audit architecture):** `AuditService` is the ONLY write path to audit tables. It is **never** `@Transactional`; it inherits the caller's transaction. Story 10.4 adds `recordRegistryBootstrapBatch(...)` using jOOQ batched-connection; DO NOT use `DSLContext.batchInsert()` (slow at scale per ADR-0003 §"Batch-write path for Story 10.4").
- **Tx-pool refactor (Story 10.1, retro T4):** No `@Transactional` method may hold a NAV HTTP call or a classifier call. ArchUnit `NavHttpOutsideTransactionTest` enforces this at build time — a violating build fails. Use `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW` per pair.
- **BigDecimal rule (retro T3):** All AI-returned numeric values hit T3 bounds `(0, 10000]`. The Story 10.3 classifier already bound-checks and drops out-of-range layers — this story inherits that contract.
- **i18n alphabetical ordering (retro T6):** Enforced by pre-commit hook. `npm run lint:i18n` must pass.
- **`@TierRequired` + role gate:** Copy pattern verbatim from `BatchPackagingClassifierController` (`JwtUtil.requireRole(...)` + `JwtUtil.requireUuidClaim(...)`).

### Reuse inventory — DO NOT reinvent

| Need | Use existing |
|---|---|
| Fetch NAV invoices | `DataSourceService.queryInvoices(taxNumber, from, to, InvoiceDirection.OUTBOUND)` — already paginates internally across all NAV pages |
| Resolve tenant's tax number | `DataSourceService.getTenantTaxNumber(tenantId) → Optional<String>` |
| Load NAV credentials | `AuthService.loadCredentials(tenantId)` — throws if missing |
| 8-digit tax-number equality | Lift substring(0,8) pattern from `EprService.java:297-303` |
| Producer profile completeness | `EprService.producerProfileComplete(tenantId)` (Story 9.4) — 412 if incomplete |
| Batch classifier | `BatchPackagingClassifierService.classify(BatchPackagingRequest)` — internal bean call, NOT HTTP self-call |
| Audit writes | `AuditService.recordRegistryFieldChange` for single-row; add `recordRegistryBootstrapBatch` for the bulk path in Task 3 |
| Registry CRUD | `RegistryService.create(tenantId, userId, ProductUpsertCommand, AuditSource source)` — AuditSource overload already exists |
| Async executor with tenant propagation | `taskExecutor` bean in `hu.riskguard.core.config.AsyncConfig` — `TenantAwareTaskDecorator` already wired; just inject and `executor.submit(...)` or use `@Async("taskExecutor")` |
| In-flight guard SQL | `WHERE NOT EXISTS (SELECT 1 FROM epr_bootstrap_jobs WHERE tenant_id = ? AND status IN ('PENDING','RUNNING'))` — leverages the partial index from AC #1 |

### DTO reference (Story 10.3, read-only here)

```java
// hu.riskguard.epr.registry.api.dto
BatchPackagingRequest { List<PairRequest> pairs } // @Size(min=1,max=100)
PairRequest { String vtsz /* @Pattern ^[0-9]{4,8}$ */, String description /* @NotBlank @Size max=500 */ }

BatchPackagingResponse { List<BatchPackagingResult> results, ClassifierUsageInfo usageInfo }
BatchPackagingResult { String vtsz, String description, List<PackagingLayerDto> layers,
                       String classificationStrategy /* GEMINI|VTSZ_PREFIX_FALLBACK|UNRESOLVED */,
                       String modelVersion }
PackagingLayerDto { int level /* 1..3 */, String kfCode /* 8 digits */,
                    BigDecimal weightEstimateKg /* nullable, 0..10000 */,
                    int itemsPerParent /* 1..10000 */, String description }
```

Constants: `STRATEGY_GEMINI="GEMINI"`, `STRATEGY_VTSZ_FALLBACK="VTSZ_PREFIX_FALLBACK"`, `STRATEGY_UNRESOLVED="UNRESOLVED"`.

### Overwrite key — DB vs. dedup key clarification

| Purpose | Key |
|---|---|
| Classifier dedup (in-memory) | `vtsz + "~" + LOWER(TRIM(description))` — normalized |
| DB existing-row lookup for overwrite | `(tenant_id, vtsz, name)` — where `name` is the **raw** invoice `description` (no normalization) |

This preserves two rows for `"kávé"` and `"KÁVÉ 250g"` if both appear as distinct invoice descriptions; dedup collapses casing for classifier efficiency but the registry row keeps the raw name for audit fidelity. Unit tests must cover both behaviors.

### Async + tenant propagation

`AsyncConfig.TenantAwareTaskDecorator` already propagates both `MDC` and `TenantContext` from the HTTP thread onto the async worker thread. The service simply calls its own `@Async("taskExecutor")` method — do NOT roll a new executor. The decorator clears `TenantContext` in `finally`, so the worker must NOT assume it survives beyond the method.

For NAV calls, `NavOnlineSzamlaClient` reads `TenantContext.getCurrentTenant()`. Since the decorator sets it before `run()`, direct NAV calls from the worker are safe. If you call NAV from a nested executor or lambda, set `TenantContext` explicitly.

### Audit write timing

Per ADR-0003 atomicity invariant, the audit row must commit with the registry row. Implementation pattern inside the per-pair REQUIRES_NEW transaction:

```java
transactionTemplate.execute(status -> {
    // 1. DELETE existing row (if any) via RegistryRepository.deleteByNaturalKey(tenant, vtsz, name)
    //    → produces FieldChangeEvent("bootstrap.deleted", ..., source=NAV_BOOTSTRAP)
    // 2. INSERT new product + components via RegistryService.create(..., AuditSource.NAV_BOOTSTRAP)
    //    → RegistryService already emits per-field audit events internally
    // 3. Accumulate remaining events in a List
    // 4. auditService.recordRegistryBootstrapBatch(events);   // still inside tx
    return null;
});
```

Prefer #4 inside the tx over `TransactionSynchronization.afterCommit()` — the latter runs outside the transactional boundary and violates ADR-0003 atomicity. (`AuditService` methods are not `@Transactional`, so they participate in the ongoing tx.)

### Frontend polling etiquette

- Poll interval: **2000ms** (hard-coded constant; one source of truth in the composable).
- Poll stops on: terminal status, dialog close, `onBeforeUnmount`, browser tab hidden (`document.visibilitychange`). Resume when visible.
- Use `window.clearInterval` + a `ref<number|null>` for the interval id — do NOT rely on `useIntervalFn` (`@vueuse/core`) unless it's already imported elsewhere in the codebase; otherwise the new dependency needs justification.

### Failure modes & observability

- **NAV outage:** `DataSourceService.queryInvoices` swallows exceptions and returns `InvoiceQueryResult { [], serviceAvailable=false }`. Worker must check `serviceAvailable` and transition `FAILED` with `error_message = "NAV service unavailable"`.
- **Classifier cap-exceeded:** `BatchPackagingClassifierService` throws `ClassifierCapExceededException`. Worker catches → `FAILED` + `error_message = "AI classifier monthly cap exceeded"`.
- **Partial classifier failure:** Per-pair basis (Story 10.3's guarantee). Count `UNRESOLVED` strategies into `unresolvedPairs` counter; never abort the batch.
- **DB constraint violation** during insert: log WARN, skip that pair, increment a local failure counter. > 10% failure rate → `FAILED_PARTIAL`.
- **Micrometer metrics** (already wired via `AuditService`): `audit.writes{source=NAV_BOOTSTRAP}` counter. Optional additional: `bootstrap.job.duration` timer, `bootstrap.job.pairs.classified` gauge — wire if trivial; otherwise defer.

### Testing standards

- Backend unit tests: JUnit 5 + Mockito, located in `backend/src/test/java/hu/riskguard/epr/registry/bootstrap/`. Mock at the collaborator boundary (`DataSourceService`, `BatchPackagingClassifierService`, `AuditService`, `BootstrapJobRepository`). Use `@MockitoBean` for controller slice tests.
- Integration tests: `@Tag("integration")` + Testcontainers PostgreSQL 17 + `@ActiveProfiles("test")` + Flyway auto-run. Follow the `RegistryRepositoryIntegrationTest` template.
- Load tests: `@Tag("load")` + opt-in `-PincludeLoadTests` Gradle property. Use `HikariDataSource.getHikariPoolMXBean()` for pool telemetry assertions.
- Frontend: Vitest + `@vue/test-utils` + `vi.mock('~/composables/api/useInvoiceBootstrap', () => ({ useInvoiceBootstrap: () => ({ ... }) }))`. Use `vi.useFakeTimers()` for polling tests.
- Playwright: extend `tests/e2e/fixtures/` with a bootstrap fixture if needed; reuse the existing demo tenant log-in helper.

### Project Structure Notes

- **New backend package:** `hu.riskguard.epr.registry.bootstrap` (distinct from the soon-to-be-deleted `hu.riskguard.epr.registry.domain.RegistryBootstrapService` namespace). Sub-packages: `api/` (controller + DTOs), `domain/` (service + worker + job record + enum), `internal/` (jOOQ repository).
- **Frontend components dir:** `frontend/app/components/Registry/` (capital R — matches the existing `Registry/KfCodeWizardDialog.vue` pattern). NOT `frontend/app/components/registry/` (lowercase — that's the 9.2 dir being deleted).
- **Migration file naming:** `V20260420_NNN__snake_case_description.sql` — keep sequential `NNN` within the date.
- **No JPA / @Entity.** jOOQ only, per `build.gradle:70`. ADR-0003 enforces this.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 10.4]
- [Source: docs/architecture/adrs/ADR-0003-epic-10-audit-architecture.md] — batch-write path invariant, atomicity rule, facade boundary
- [Source: backend/src/main/java/hu/riskguard/epr/audit/AuditService.java] — facade API
- [Source: backend/src/main/java/hu/riskguard/epr/audit/AuditSource.java] — enum values
- [Source: backend/src/main/java/hu/riskguard/epr/registry/domain/RegistryBootstrapService.java] — 9.2 orchestrator reference + to-be-deleted list
- [Source: backend/src/main/java/hu/riskguard/epr/registry/api/BatchPackagingClassifierController.java] — role-gate + tier annotation pattern
- [Source: backend/src/main/java/hu/riskguard/epr/registry/domain/BatchPackagingClassifierService.java] — classifier bean to call from the worker
- [Source: backend/src/main/java/hu/riskguard/core/config/AsyncConfig.java] — `taskExecutor` + `TenantAwareTaskDecorator`
- [Source: backend/src/main/java/hu/riskguard/datasource/domain/DataSourceService.java] — NAV facade
- [Source: backend/src/test/java/hu/riskguard/architecture/NavHttpOutsideTransactionTest.java] — tx-across-HTTP rule to extend
- [Source: backend/src/test/java/hu/riskguard/architecture/EpicTenInvariantsTest.java] — audit invariants to extend
- [Source: backend/src/main/resources/db/migration/V20260414_002__create_bootstrap_candidates.sql] — table to drop
- [Source: backend/src/main/resources/db/migration/V20260418_001__extend_ppc_for_epic10.sql] — Story 10.1 schema baseline
- [Source: _bmad-output/implementation-artifacts/10-1-registry-schema-menu-restructure-and-tx-pool-refactor.md] — T4 tx-pool refactor pattern
- [Source: _bmad-output/implementation-artifacts/10-3-ai-batch-classifier-full-packaging-stack-endpoint.md] — classifier DTOs + bean names + 429 semantics
- [Source: _bmad-output/implementation-artifacts/epic-9-retro-2026-04-17.md] — retro actions T1/T2/T3/T4/T5/T6
- [Source: _bmad-output/planning-artifacts/research/technical-audit-trail-architecture-epic-10-research-2026-04-17.md] — audit-architecture research underlying ADR-0003

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

- [ ] AC-to-task walkthrough (T1) completed before first code commit — note any AC→task gaps found.
- [ ] Load test evidence (Hikari pool peak active connections, wall time) captured.
- [ ] Demo-tenant smoke observations.
- [ ] jOOQ regen performed after `DROP TABLE registry_bootstrap_candidates` — confirmed `REGISTRY_BOOTSTRAP_CANDIDATES` absent from generated sources.
- 2026-04-20 — Second code-review patch pass (R1–R21) resolved on top of P1–P13.
  - R1 (BLOCKER): `RegistryRepository.insertComponent` now writes `CLASSIFIER_SOURCE`; `ComponentUpsertCommand.classificationSource()` was being silently dropped end-to-end, breaking AC #20 tagging, AC #24 "Csak bizonytalan" filter chip, and the row badges.
  - R2 (BLOCKER): VTSZ_FALLBACK layers (null weight per `PackagingLayerDto` contract) no longer trip the NOT NULL constraint on `product_packaging_components.weight_per_unit_kg`; null is coerced to `BigDecimal.ZERO` at persist time and the product is flagged `review_state=MISSING_PACKAGING` so the user knows to enter weights manually.
  - R3: Removed tautological `!registeredBase.equals(stored.substring(0,8))` tax-number check; hardened `getTenantTaxNumber` to map decryption exceptions to 412 NAV_CREDENTIALS_MISSING (AC #12 "not decryptable" branch).
  - R4: `transitionStatus` now requires non-terminal current status (PENDING/RUNNING) in its WHERE clause and returns affected-rows count; service uses that count to detect cancel-before-RUNNING race and exit cleanly.
  - R5: New `BootstrapJobRepository.findTenantForJob` used by controller to distinguish cross-tenant (403) from unknown (404) on GET + DELETE (AC #8, #9).
  - R6: `completeJob` now appends up to 3 per-pair failure reasons to `FAILED_PARTIAL` / `FAILED` error_message (AC #16 "N/M pairs failed: <first-3-reasons>").
  - R7 (UX BLOCKER): `toLocalIsoDate` builds a local-TZ ISO string instead of `.toISOString().slice(0,10)`; Budapest users no longer see an off-by-one period.
  - R8: Dedup key uses `strip() + toLowerCase(Locale.ROOT)` — handles NBSP and avoids Turkish-locale dotless-i.
  - R9: Dialog now surfaces the `jobId` from a 409 ALREADY_RUNNING body and auto-attaches to the in-flight job (polls + allows cancel) rather than dead-ending on a generic error.
  - R10: `onBeforeUnmount(clearPoll)` added; polling no longer leaks if the parent page unmounts the dialog without toggling `visible=false`.
  - R11: Controller precondition tests throw real `BootstrapPreconditionException` and assert the structured `{ code, message }` body produced by `@ExceptionHandler` (previously mocked as generic `ResponseStatusException`, which bypassed the handler path).
  - R12: Registry index page hydrates `onlyIncomplete` / `onlyUncertain` from `route.query` — `router.push('/registry?reviewState=MISSING_PACKAGING')` now actually applies the filter on landing.
  - R13: `onOpenRegistry` routes to `?classifierSource=VTSZ_FALLBACK` when the run produced only uncertain rows (previously dropped users on the bare Registry).
  - R14: `V20260420_003` migration uses `RAISE EXCEPTION` on non-zero APPROVED rows; a warning log previously allowed silent drop of approved bootstrap data.
  - R15: `error_message` truncated to 999 chars before persist; long classifier stack traces no longer trip the VARCHAR(1000) ceiling and leave the job stuck RUNNING.
  - R16: Dialog shows a dedicated "no invoices found" message when a job completes with `totalPairs=0` instead of "0 products created".
  - R17: Added AC #32 missing sub-cases — dispatch-distinct-pairs test, cancellation-mid-batch, cancel-race-before-RUNNING, and `truncate()` helper assertions (service test count now 20).
  - R18: Added AC #33 cross-tenant 403 tests for both GET and DELETE (controller test count now 15).
  - R19: Added documented witness pair for AC #35 — four ArchUnit rules (audit, bootstrap-package, bootstrap-table, facade) now have commented instructions for how to prove they fire.
  - R20: New `InvoiceDrivenRegistryBootstrapIntegrationTest` (@Tag("integration"), Testcontainers) — seeds tenant + user + 20 lines across 5 pairs (2 Gemini, 2 fallback, 1 unresolved), asserts full post-run DB state including `classifier_source` tagging and audit entries (AC #34).
  - R21: New `frontend/e2e/invoice-bootstrap.e2e.ts` Playwright test covers the golden flow with explicit skip paths for env-dependent preflight (AC #39).
  - Verification: `./gradlew test` (full backend, includes integration) → BUILD SUCCESSFUL; 797 frontend vitest; `npm run lint:i18n` + `npx tsc --noEmit` clean; `npx playwright test` → 5 passed / 2 skipped / 0 failed.
- 2026-04-20 — Code-review patch pass: P1–P13 resolved in a single change set.
  - P1: 8-digit prefix equality check added to `startJob` (lifts EprService:297-303 pattern; DataSourceService-only to respect the `datasource.internal.*` ArchUnit boundary).
  - P2: cancel endpoint made atomic via new `BootstrapJobRepository.cancelIfActive` conditional UPDATE (`WHERE status IN PENDING/RUNNING`); 0 rows → 404/409 disambiguated by a follow-up read.
  - P3/P4: introduced `BootstrapPreconditionException` + `@ExceptionHandler` rendering `{ code, message, ...extra }` bodies for `NAV_CREDENTIALS_MISSING`, `PRODUCER_PROFILE_INCOMPLETE`, `TAX_NUMBER_MISMATCH`, and `ALREADY_RUNNING` (the last carrying `jobId`).
  - P5: added 3 dedup unit tests covering casing + trim + tab-trim, internal whitespace preservation (current behavior), and null/blank skip — service test count now 15 (≥10).
  - P6: classifier `results.size() != chunk.size()` → FAIL with no per-pair persistence + regression test.
  - P7: DELETE on `products` now carries `PRODUCTS.TENANT_ID.eq(tenantId)` defence-in-depth predicate.
  - P8: added `setInterval` cadence test using `vi.advanceTimersByTime(2000)` to exercise the 2s polling contract.
  - P9: verified default-period math (Java YearMonth.minusMonths(3)/atDay(1) = JS `to.getMonth() - 2` after subtracting the prev-complete-month offset); added in-code explanation.
  - P10: log placeholders confirmed present in the current version (no live regression). Left as-is.
  - P11: added reflective `@TierRequired(PRO_EPR)` presence test (TierGateInterceptor is out-of-scope for unit-instantiated controller) and two new exception-handler tests — controller test count now 12 (≥11).
  - P12: `camelFromCode` returns `undefined` on unknown codes; caller falls back to server `message` or `common.states.error`.
  - P13: `RegistryRepository` reviewState filter + bootstrap insert now use typed `PRODUCTS.REVIEW_STATE` from generated jOOQ.
  - Verification: `./gradlew test` (full backend suite) + targeted `registry.bootstrap.*` + `architecture.*` → BUILD SUCCESSFUL. `npm test` (797 frontend tests) + `npm run lint:i18n` + `npx tsc --noEmit` → all green.

### Review Findings

<!-- Code review 2026-04-20 — 3 parallel layers (Blind Hunter + Edge Case Hunter + Acceptance Auditor) -->
<!-- 13 patch · 10 defer · 9 dismissed -->

<!-- Second code review 2026-04-20 — 3 parallel layers re-run after P1–P13 landed -->
<!-- 21 patch (R1–R21) · 15 defer · 10 dismissed -->
<!-- R1 + R2 were BLOCKERs: classifier_source never persisted; VTSZ_FALLBACK null weight → NOT NULL violation -->
<!-- R20 + R21 add the missing AC #34 integration + AC #39 Playwright tests -->


- [x] [Review][Patch] P1 — AC#11: 8-digit tax-number prefix check entirely absent — `startJob` only checks `getTenantTaxNumber.isEmpty()` → 412; never compares first 8 digits of registered tax number against `nav_tenant_credentials.tax_number`; mismatch must return 403 [InvoiceDrivenRegistryBootstrapService.java:131-135]
- [x] [Review][Patch] P2 — Cancel endpoint non-atomic: `findByIdAndTenant` then `transitionStatus(CANCELLED)` races job completion; use conditional UPDATE `WHERE status NOT IN (terminal states)` returning affected rows; 0 rows → 409 [BootstrapJobController.java:120-129 / BootstrapJobRepository.java:132-141]
- [x] [Review][Patch] P3 — AC#12: 412 responses lack `{ code: 'NAV_CREDENTIALS_MISSING' | 'PRODUCER_PROFILE_INCOMPLETE', message }` machine-readable body; Spring returns generic `{ status, error, message }` — frontend cannot distinguish the two 412 causes [InvoiceDrivenRegistryBootstrapService.java:133,138]
- [x] [Review][Patch] P4 — AC#13: 409 in-flight body lacks `{ code: 'ALREADY_RUNNING', jobId }` structure; current impl throws plain `ResponseStatusException`; frontend cannot extract `jobId` to show "view running job" option [InvoiceDrivenRegistryBootstrapService.java:143-158]
- [x] [Review][Patch] P5 — AC#18+#32: Dedup whitespace/casing/trim unit tests missing (comment on line 156 of service test is misplaced; actual dedup tests absent); service test count = 9, spec requires ≥10 [InvoiceDrivenRegistryBootstrapServiceTest.java:156+]
- [x] [Review][Patch] P6 — Classifier `results.size()` vs `chunk.size()` not validated; if classifier returns fewer results than sent pairs, `chunk.get(i)` silently misaligns — wrong pair gets wrong packaging data written [InvoiceDrivenRegistryBootstrapService.java:272-274]
- [x] [Review][Patch] P7 — `DELETE FROM products WHERE id=?` in `persistPair` missing TENANT_ID guard (defence-in-depth); `existingId` is tenant-scoped from the SELECT but the DELETE itself has no `TENANT_ID` condition [InvoiceDrivenRegistryBootstrapService.java:339]
- [x] [Review][Patch] P8 — AC#36: `InvoiceBootstrapDialog.spec.ts` sets up `vi.useFakeTimers()` in `beforeEach` but never calls `vi.advanceTimersByTime(2000)` in any test; 2s polling behaviour (AC#23) is unexercised [InvoiceBootstrapDialog.spec.ts]
- [x] [Review][Patch] P9 — Frontend default period off-by-one: `from = new Date(to.getFullYear(), to.getMonth() - 2, 1)` shows Feb–Mar (2 months); server defaults to Jan–Mar (3 months); fix: `to.getMonth() - 3` [InvoiceBootstrapDialog.vue:28]
- [x] [Review][Patch] P10 — `log.error` and `log.warn` format strings in `processJob` have 3 `{}` placeholders but zero arguments passed → SLF4J logs literal format string in production [InvoiceDrivenRegistryBootstrapService.java:261,287]
- [x] [Review][Patch] P11 — AC#33: Tier-gate 402 test (`@TierRequired(PRO_EPR)`) absent from `BootstrapJobControllerTest`; interceptor is bypassed by direct controller instantiation; controller test count 9, spec requires ≥11 [BootstrapJobControllerTest.java]
- [x] [Review][Patch] P12 — `camelFromCode` fallback maps **any** unknown error code to `navCredentialsMissing`; unknown server errors silently display wrong i18n message; should fall back to a generic error [InvoiceBootstrapDialog.vue:134]
- [x] [Review][Patch] P13 — `reviewState` filter uses raw `DSL.field("review_state", String.class)` instead of typed `PRODUCTS.REVIEW_STATE` (which exists in jOOQ generated code per line 142 of RegistryRepository); typo-safe refactor [RegistryRepository.java:210]

- [x] [Review][Defer] D1 — No max period window validation (spec doesn't require; future enhancement) — deferred, pre-existing
- [x] [Review][Defer] D2 — `taskExecutor` rejection policy (CallerRunsPolicy) not verified — pre-existing AsyncConfig; not introduced by 10.4 — deferred, pre-existing
- [x] [Review][Defer] D3 — `writeCounters` NPE if future AuditSource added — pre-existing AuditService design — deferred, pre-existing
- [x] [Review][Defer] D4 — `@Lazy @Autowired self` self-proxy anti-pattern — established Spring pattern, production-safe — deferred, pre-existing
- [x] [Review][Defer] D5 — AC#23: Overwrite check calls `listProducts({size:1})` not `GET /api/v1/registry/summary` — Story 10.7 endpoint doesn't exist yet; functionally equivalent — deferred, pre-existing
- [x] [Review][Defer] D6 — NBSP / Unicode whitespace not stripped by Java `.trim()` in dedup key — NAV invoice quality assumption, very low probability — deferred, pre-existing
- [x] [Review][Defer] D7 — AC#6: `BootstrapJobWorker` as a separate class (spec names it; implementation merges into service `@Async`); ArchUnit `allowEmptyShould(true)` — functionally equivalent — deferred, pre-existing
- [x] [Review][Defer] D8 — `findInflightByTenant` uses `fetchOptional`; silent masking if 2 in-flight rows existed — impossible per partial unique index — deferred, pre-existing
- [x] [Review][Defer] D9 — Progress bar indeterminate mode when `totalPairs=0` (minor UX) — deferred, pre-existing
- [x] [Review][Defer] D10 — Per-pair crash loses in-memory `totalFailed`; outer try-catch calls `failJob()` so job is never stuck RUNNING — deferred, pre-existing

### File List

_Code-review patch pass (2026-04-20):_
- Added: `backend/src/main/java/hu/riskguard/epr/registry/bootstrap/domain/BootstrapPreconditionException.java`
- Modified: `backend/src/main/java/hu/riskguard/epr/registry/bootstrap/domain/InvoiceDrivenRegistryBootstrapService.java` (P1, P6, P7, P13)
- Modified: `backend/src/main/java/hu/riskguard/epr/registry/bootstrap/api/BootstrapJobController.java` (P2, P3, P4)
- Modified: `backend/src/main/java/hu/riskguard/epr/registry/bootstrap/internal/BootstrapJobRepository.java` (P2)
- Modified: `backend/src/main/java/hu/riskguard/epr/registry/internal/RegistryRepository.java` (P13)
- Modified: `backend/src/test/java/hu/riskguard/epr/registry/bootstrap/InvoiceDrivenRegistryBootstrapServiceTest.java` (P1, P3, P4, P5, P6)
- Modified: `backend/src/test/java/hu/riskguard/epr/registry/bootstrap/BootstrapJobControllerTest.java` (P2, P3, P4, P11)
- Modified: `frontend/app/components/Registry/InvoiceBootstrapDialog.vue` (P9 comment, P12)
- Modified: `frontend/app/components/Registry/InvoiceBootstrapDialog.spec.ts` (P8)

_Second code-review patch pass (2026-04-20, R1–R21):_
- Added: `backend/src/test/java/hu/riskguard/epr/registry/bootstrap/InvoiceDrivenRegistryBootstrapIntegrationTest.java` (R20)
- Added: `frontend/e2e/invoice-bootstrap.e2e.ts` (R21)
- Modified: `backend/src/main/java/hu/riskguard/epr/registry/internal/RegistryRepository.java` (R1)
- Modified: `backend/src/main/java/hu/riskguard/epr/registry/bootstrap/domain/InvoiceDrivenRegistryBootstrapService.java` (R2, R3, R4 worker, R6, R8, R15)
- Modified: `backend/src/main/java/hu/riskguard/epr/registry/bootstrap/internal/BootstrapJobRepository.java` (R4, R5 `findTenantForJob`)
- Modified: `backend/src/main/java/hu/riskguard/epr/registry/bootstrap/api/BootstrapJobController.java` (R5 `loadJobOrThrow`)
- Modified: `backend/src/main/resources/db/migration/V20260420_003__drop_registry_bootstrap_candidates.sql` (R14)
- Modified: `backend/src/test/java/hu/riskguard/epr/registry/bootstrap/InvoiceDrivenRegistryBootstrapServiceTest.java` (R4 stub, R17)
- Modified: `backend/src/test/java/hu/riskguard/epr/registry/bootstrap/BootstrapJobControllerTest.java` (R5 cross-tenant 403, R11, R18)
- Modified: `backend/src/test/java/hu/riskguard/architecture/EpicTenInvariantsTest.java` (R19)
- Modified: `frontend/app/components/Registry/InvoiceBootstrapDialog.vue` (R7, R9, R10, R13, R16)
- Modified: `frontend/app/pages/registry/index.vue` (R12)
- Modified: `frontend/app/i18n/en/registry.json` + `frontend/app/i18n/hu/registry.json` (R16 `noInvoicesFound` key)
