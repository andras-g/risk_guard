# Story 3.7: 24h Background Monitoring Cycle

Status: done

## Story

As a User,
I want the system to automatically monitor my watchlist for status changes every 24 hours,
so that I am proactively informed of any risks without manual effort.

## Acceptance Criteria

### AC1 — WatchlistMonitor Scheduled Job Exists
**Given** a Spring Boot application with `@EnableScheduling` (already enabled from Story 3.5),
**When** the `WatchlistMonitor` triggers on its configured cron schedule (default: runs after AsyncIngestor completes, e.g., `0 0 4 * * ?` — 04:00 UTC / 06:00 Budapest),
**Then** it retrieves all actively monitored partners from `watchlist_entries` (cross-tenant, via `NotificationService.getMonitoredPartners()`),
**And** for each partner it re-evaluates the verdict using the latest `company_snapshots` data,
**And** it compares the new verdict status against the `last_verdict_status` stored on the `watchlist_entries` row.

### AC2 — Status Change Detection and Logging
**Given** a watchlist entry where the previous verdict was `RELIABLE`,
**When** the WatchlistMonitor re-evaluates and the new verdict is `AT_RISK`,
**Then** the deviation is logged at INFO level: `"Verdict changed tax_number=1234**** tenant={tenantId} previous=RELIABLE new=AT_RISK"`,
**And** a `PartnerStatusChanged` application event is published (existing event record from Story 2.3),
**And** the `watchlist_entries` row is updated with the new `last_verdict_status` and `last_checked_at` timestamp.

### AC3 — Denormalized Verdict Columns on watchlist_entries
**Given** the `watchlist_entries` table,
**When** Story 3.7 migrations run,
**Then** two new columns exist: `last_verdict_status VARCHAR(20)` and `last_checked_at TIMESTAMPTZ`,
**And** the `NotificationRepository.findByTenantId()` lateral join on screening tables is replaced by reading these denormalized columns directly,
**And** the acknowledged tech debt from Story 3.6 is resolved.

### AC4 — PartnerStatusChanged Event Listener in Notification Module
**Given** a `PartnerStatusChanged` event is published (by WatchlistMonitor or by user-initiated search),
**When** the `NotificationService` event listener receives it,
**Then** it updates the matching `watchlist_entries` row with the new verdict status and timestamp,
**And** if the tax number is not on any watchlist, the event is silently ignored (no error).

### AC5 — Transient Failure Handling with Per-Entry Retry
**Given** the WatchlistMonitor is processing a list of watchlist entries,
**When** a data source is unavailable for a specific partner (verdict evaluates to `INCOMPLETE`/`UNAVAILABLE`),
**Then** the existing `last_verdict_status` is NOT overwritten with INCOMPLETE (transient failure does not constitute a status change),
**And** the failure is logged at WARN level with masked tax number,
**And** processing continues to the next entry (no list-wide abort),
**And** the `last_checked_at` is still updated to record that monitoring was attempted.

### AC6 — Demo Mode Validates Infrastructure
**Given** `riskguard.data-source.mode=demo`,
**When** the WatchlistMonitor triggers,
**Then** it iterates all watchlist entries and re-evaluates verdicts using demo fixture data,
**And** since demo data is static, no status changes are detected (but the infrastructure is validated),
**And** `last_checked_at` is updated on all entries,
**And** a log line at INFO level is written: `"WatchlistMonitor completed [demo mode] entries_processed=N changes_detected=0"`.

### AC7 — Rate Limiting Between Evaluations
**Given** the WatchlistMonitor is processing entries,
**When** it evaluates each partner,
**Then** a configurable inter-evaluation delay (property: `risk-guard.watchlist-monitor.delay-between-evaluations-ms`, default: 200ms) is applied between successive evaluations,
**And** this delay is skipped in demo mode.

### AC8 — Health Indicator Reports Monitor Status
**Given** the Spring Boot Actuator is enabled,
**When** `GET /actuator/health` is called,
**Then** the response includes a `watchlistMonitor` component with:
  - `status: UP` (monitor has run at least once or has never run — first run shows UP with `lastRun: never`)
  - `lastRun`: ISO-8601 timestamp of last completed run, or `"never"`
  - `lastEntriesProcessed`: count of entries evaluated
  - `lastChangesDetected`: count of status changes found
  - `lastErrorCount`: count of evaluation failures

### AC9 — No Regressions
**Given** the new WatchlistMonitor, event listener, and migration,
**When** `./gradlew check` and frontend tests are run,
**Then** all existing tests pass with zero regressions,
**And** new unit tests cover: (a) status change detection (RELIABLE→AT_RISK); (b) no change detected; (c) transient failure handling (INCOMPLETE not overwriting); (d) demo mode run; (e) rate limit delay; (f) event listener updates watchlist entry; (g) event listener ignores non-watchlist tax numbers,
**And** the lateral join tech debt in `NotificationRepository.findByTenantId()` is removed.

## Tasks / Subtasks

### Backend Tasks

- [x] **BE-1:** Create Flyway migration `V20260318_003__add_watchlist_verdict_columns.sql` — adds `last_verdict_status VARCHAR(20)` and `last_checked_at TIMESTAMPTZ` columns to `watchlist_entries`. Backfill existing rows via a lateral join on `verdicts`/`company_snapshots` (one-time migration data fill). (AC3)
- [x] **BE-2:** Create `WatchlistMonitor.java` in `hu.riskguard.notification.domain` — `@Component` with `@Scheduled` cron (property: `risk-guard.watchlist-monitor.cron`, default: `0 0 4 * * ?`). Iterates all watchlist entries cross-tenant via `notificationService.getMonitoredPartners()`. For each entry: loads latest snapshot from `ScreeningService`, re-evaluates verdict via `VerdictEngine.evaluate()`, compares against `last_verdict_status`, publishes `PartnerStatusChanged` if changed. (AC1, AC2, AC5, AC6, AC7)
- [x] **BE-3:** Create `WatchlistMonitorHealthState.java` in `hu.riskguard.core.config` — thread-safe `AtomicReference<RunSnapshot>` pattern (identical to `AsyncIngestorHealthState`). Records `lastRun`, `lastEntriesProcessed`, `lastChangesDetected`, `lastErrorCount`. (AC8)
- [x] **BE-4:** Create `WatchlistMonitorHealthIndicator.java` in `hu.riskguard.core.config` — Spring Boot `HealthIndicator` reporting monitor status from `WatchlistMonitorHealthState`. Always UP (partial failures are non-fatal). (AC8)
- [x] **BE-5:** Add `WatchlistMonitor` configuration to `RiskGuardProperties.java` — inner class `WatchlistMonitor` with `cron`, `delayBetweenEvaluationsMs`. (AC7)
- [x] **BE-6:** Add `watchlist-monitor` config block to `application.yml` and `application-test.yml` (disabled cron in test). (AC7)
- [x] **BE-7:** Create `PartnerStatusChangedListener.java` in `hu.riskguard.notification.domain` — `@ApplicationModuleListener` consuming `PartnerStatusChanged` events. Updates matching `watchlist_entries` row(s) with new verdict status and timestamp. Silently ignores events for tax numbers not on any watchlist. (AC4)
- [x] **BE-8:** Add methods to `NotificationRepository.java`: `updateVerdictStatus(UUID tenantId, String taxNumber, String verdictStatus, OffsetDateTime checkedAt)` and `findWatchlistEntriesByTaxNumber(String taxNumber)` (cross-tenant, for event listener matching). (AC3, AC4)
- [x] **BE-9:** Add `getLatestSnapshotWithVerdict(UUID tenantId, String taxNumber)` method to `ScreeningService.java` facade — returns current snapshot data + last verdict status needed by WatchlistMonitor for re-evaluation. (AC1, AC2)
- [x] **BE-10:** Refactor `NotificationRepository.findByTenantId()` — remove the lateral join on `verdicts`/`company_snapshots` tables. Read `last_verdict_status` and `last_checked_at` directly from `watchlist_entries` columns. This resolves the acknowledged tech debt from Story 3.6. (AC3)
- [x] **BE-11:** Update `NotificationService.getMonitoredPartners()` — extend `WatchlistPartner` record (or create a new `MonitoredPartner` record) to include `lastVerdictStatus` so the monitor can compare old vs new. (AC2)

### Frontend Tasks

- [x] **FE-1:** Update `WatchlistTable.vue` — verify it reads `currentVerdictStatus` and `lastCheckedAt` from the API response (these fields will now be populated via denormalized columns instead of the lateral join; no frontend code change expected if the DTO shape is unchanged). (AC3)
- [x] **FE-2:** Add i18n keys to `hu/notification.json` and `en/notification.json` for any new monitoring-related UI text (e.g., `notification.watchlist.lastMonitored`, `notification.watchlist.monitoringActive`). (AC3)

### Testing Tasks

- [x] **TEST-1:** `WatchlistMonitorTest.java` in `hu.riskguard.notification.domain` — unit tests covering: status change detection (RELIABLE→AT_RISK publishes event + updates entry), no change (same status → no event), transient failure (INCOMPLETE not overwriting existing status), demo mode run (no changes detected), rate limit delay applied, empty watchlist (no-op), exception isolation per entry. (AC9)
- [x] **TEST-2:** `WatchlistMonitorHealthIndicatorTest.java` in `hu.riskguard.core.config` — unit tests: never run → UP with lastRun:never, clean run → UP with counts, partial failure → UP with non-zero errorCount. (AC9)
- [x] **TEST-3:** `PartnerStatusChangedListenerTest.java` in `hu.riskguard.notification.domain` — unit tests: event updates matching watchlist entry, event with non-watchlisted tax number is ignored, multiple tenants with same tax number all updated. (AC9)
- [x] **TEST-4:** Verify `./gradlew check` passes — all existing + new tests green. Confirm lateral join removal does not break `WatchlistTable.spec.ts` or `NotificationServiceTest`. (AC9)

### Review Follow-ups (AI)

- [x] [AI-Review][HIGH] **H1:** Fix stale Javadoc `@link` references — `PartnerStatusChanged.java:13` and `WatchlistMonitorHealthState.java:9` reference `hu.riskguard.notification.domain.WatchlistMonitor` but class is at `hu.riskguard.screening.domain.WatchlistMonitor`
- [x] [AI-Review][HIGH] **H2:** Update `notification/package-info.java` — line 14 says "Cross-module dependencies: none" (false), lines 15-17 reference lateral join and "planned for Story 3.7" (stale — Story 3.7 is done)
- [x] [AI-Review][HIGH] **H3:** Update `WatchlistController.java` Javadoc (lines 25-28) — still says "these fields return null" and "planned for Story 3.7"; enrichment is now implemented
- [x] [AI-Review][MEDIUM] **M1:** Add null JWT guard to `IdentityController.updateLanguage()` and `getMandates()` — BUGFIX-9 only guarded `me()`, same NPE risk exists on these endpoints
- [x] [AI-Review][MEDIUM] **M2:** `SnapshotVerdictResult.transientFailure()` is always `false` — `getLatestSnapshotWithVerdict()` hardcodes `false`, making the WatchlistMonitor transient-failure branch (AC5) dead code. Add freshness-based detection or remove the dead branch
- [x] [AI-Review][MEDIUM] **M3:** Extract duplicated `maskTaxNumber()` from `AsyncIngestor` and `WatchlistMonitor` to shared utility in `core.util` (DRY violation, story Dev Notes already recommended this)
- [x] [AI-Review][MEDIUM] **M4:** Fix File List entry `frontend/app/risk-guard-tokens.json` (line 468) — file does not exist at that path; the actual file is `risk-guard-tokens.json` (project root), copied to backend classpath
- [x] [AI-Review][LOW] **L1:** Update story Dev Notes architecture section (lines 111, 262-263) to reflect actual placement: `WatchlistMonitor` is in `screening.domain`, not `notification.domain`
- [x] [AI-Review][LOW] **L2:** Update `NotificationRepository.WatchlistEntryRecord` Javadoc (line 263) — still says "from the lateral join"; should say "from denormalized columns"
- [x] [AI-Review][LOW] **L3:** Fix File List entry `frontend/app/components/Common/LocaleSwitcherLight.vue` (line 433) — file was NOT created; `LocaleSwitcher.vue` was modified with a `variant` prop instead. Also add undocumented changes: `frontend/app/app.vue`, `backend/build.gradle`

### Review Follow-ups Round 2 (AI)

- [x] [AI-Review-R2][HIGH] **R2-H1:** Fix stale event catalog in `core/events/package-info.java` — still listed `PartnerStatusChanged` as "placeholder for Story 2.3+" in `screening.domain.events`. Updated to document the event's actual location (`core.events`), publishers, consumers, and corrected the PII policy to acknowledge `taxNumber` field with masking requirement.
- [x] [AI-Review-R2][HIGH] **R2-H2:** Sanitize exception logging in `WatchlistMonitor.java:133` — `log.error(..., e)` could leak PII (tax numbers) from downstream exception stack traces. Changed to log only exception class and message, omitting the full stack trace.
- [x] [AI-Review-R2][MEDIUM] **R2-M1:** Fix double-write on status change in WatchlistMonitor — the monitor called `updateVerdictStatus()` directly AND published a `PartnerStatusChanged` event (whose listener also calls `updateVerdictStatus()`). Refactored: on status change, only the event is published (listener handles the update); on no change, the monitor updates directly to refresh `last_checked_at`.
- [x] [AI-Review-R2][MEDIUM] **R2-M2:** Fix `findByTenantIdAndTaxNumber()` missing verdict columns — the duplicate-check query only selected 7 columns, causing `verdictStatus` and `lastCheckedAt` to be null in duplicate responses. Added `last_verdict_status` and `last_checked_at` to the SELECT.

## Dev Notes

### Architecture Fit

- **Module placement (actual):** `WatchlistMonitor` lives in `hu.riskguard.screening.domain` — relocated from the originally-planned `notification.domain` to avoid a Spring Modulith circular dependency (`screening ↔ notification`). Both background jobs (`AsyncIngestor`, `WatchlistMonitor`) live together in the screening module and call the `NotificationService` facade for watchlist data access.
- **Relationship to AsyncIngestor:** The `AsyncIngestor` (Story 3.5) refreshes raw snapshot data at 02:00 UTC. The `WatchlistMonitor` (this story) runs AFTER at 04:00 UTC, re-evaluates verdicts from the freshly-updated snapshots, and detects changes. They are complementary — ingestor handles data freshness, monitor handles change detection. Do NOT merge them.
- **Cross-module data access:** `WatchlistMonitor` needs screening data (latest snapshot + verdict evaluation). Per architecture communication matrix: **Need return value → facade call**. Add `getLatestSnapshotWithVerdict()` to `ScreeningService` facade. Do NOT import `ScreeningRepository` directly from notification module.
- **Event-driven architecture:** `PartnerStatusChanged` already exists (published by `ScreeningService.search()` for user-initiated searches). This story adds a SECOND publisher (WatchlistMonitor) and the FIRST consumer (`PartnerStatusChangedListener` in notification module). The event is the bridge between screening and notification modules.
- **Denormalization strategy:** Adding `last_verdict_status` and `last_checked_at` to `watchlist_entries` eliminates the cross-module lateral join tech debt from Story 3.6. These columns are updated by: (1) the event listener when any `PartnerStatusChanged` fires, and (2) the WatchlistMonitor directly during its cycle. This means user-initiated searches also update watchlist entries reactively.

### WatchlistMonitor Implementation Pattern

```java
@Component
public class WatchlistMonitor {

    @Scheduled(cron = "${risk-guard.watchlist-monitor.cron:0 0 4 * * ?}")
    public void monitor() {
        List<MonitoredPartner> partners = notificationService.getMonitoredPartners();
        int processed = 0, changes = 0, errors = 0;
        for (MonitoredPartner partner : partners) {
            try {
                TenantContext.setCurrentTenant(partner.tenantId());
                // Get latest snapshot data via screening facade
                SnapshotVerdictResult current = screeningService
                    .getLatestSnapshotWithVerdict(partner.tenantId(), partner.taxNumber());
                if (current == null || current.isTransientFailure()) {
                    // Don't overwrite existing status with INCOMPLETE
                    notificationRepository.updateCheckedAt(partner.tenantId(), partner.taxNumber(), now);
                    if (current != null && current.isTransientFailure()) errors++;
                    continue;
                }
                String newStatus = current.verdictStatus();
                String oldStatus = partner.lastVerdictStatus();
                if (!Objects.equals(oldStatus, newStatus)) {
                    changes++;
                    eventPublisher.publishEvent(PartnerStatusChanged.of(
                        current.verdictId(), partner.tenantId(), oldStatus, newStatus));
                }
                notificationRepository.updateVerdictStatus(
                    partner.tenantId(), partner.taxNumber(), newStatus, now);
                processed++;
            } catch (Exception e) {
                errors++;
                log.error("Monitor entry failed tenant={}", partner.tenantId(), e);
            } finally {
                TenantContext.clear();
                sleepIfNeeded();
            }
        }
        healthState.recordRun(processed, changes, errors);
    }
}
```

### Critical Anti-Patterns to Avoid

1. **DO NOT re-fetch data from external sources.** The WatchlistMonitor reads EXISTING snapshots that were already refreshed by the AsyncIngestor. It does NOT call `DataSourceService.fetchCompanyData()`. It only re-evaluates verdicts from cached snapshots.
2. **DO NOT overwrite verdict with INCOMPLETE on transient failure.** If the latest snapshot is stale or a source was unavailable, the existing `last_verdict_status` must be preserved. Only genuine status transitions (RELIABLE→AT_RISK, AT_RISK→RELIABLE, etc.) constitute a "change."
3. **DO NOT import from `screening.internal`** — use `ScreeningService` facade only. The `VerdictEngine` is a public domain class in `screening.domain` and CAN be imported directly (it's a pure function, no Spring bean dependencies).
4. **DO NOT create a new event type.** Reuse the existing `PartnerStatusChanged` record from `screening.domain.events`. The notification module already has visibility to screening's public API.
5. **DO NOT use `@Async` on the scheduled method.** Follow the AsyncIngestor pattern: sequential processing on the scheduler thread with configurable delay.
6. **DO NOT set TenantContext without a finally block.** Always `TenantContext.clear()` in finally — copied from `AsyncIngestor` and `CompanyDataAggregator.withTenant()` patterns.

### Spring Boot 4 / Modulith Notes

- **Event listener:** Use `@ApplicationModuleListener` (Spring Modulith annotation) on the `PartnerStatusChangedListener` method. This is the preferred way to consume cross-module events in Spring Modulith. It ensures the listener is registered as a module-aware event handler. See `TenantContextSwitchedEventListener` for reference.
- **Health package:** Use `org.springframework.boot.health.contributor.{Health, HealthIndicator, Status}` (moved in Spring Boot 4, learned in Stories 3.4 and 3.5).
- **Scheduler thread:** `@Scheduled` runs on the `taskScheduler` thread pool. Since AsyncIngestor already uses the scheduler at 02:00 and WatchlistMonitor at 04:00, there's no contention (sequential by time). If they ever overlap, Spring's default single-thread scheduler would serialize them — acceptable for MVP.
- **Event publishing from WatchlistMonitor:** Use `ApplicationEventPublisher.publishEvent()` directly. The `PartnerStatusChanged` event published here is the SAME event type published by `ScreeningService.search()`. The listener doesn't care who publishes.
- **Transaction boundary for events:** The WatchlistMonitor is NOT `@Transactional` (it processes entries in a loop). Each `publishEvent()` call is standalone. The `PartnerStatusChangedListener` should use `@ApplicationModuleListener` which by default runs in the publishing thread's context. If you need transactional guarantees on the listener side, annotate the listener method with `@Transactional`.

### VerdictEngine Re-evaluation Strategy

The WatchlistMonitor needs to re-evaluate verdicts from existing snapshot data. Two approaches:

**Option A (Recommended): Read latest verdict directly from screening facade.**
- `ScreeningService.getLatestSnapshotWithVerdict(tenantId, taxNumber)` returns the most recent `verdicts` row status.
- No re-computation needed — the verdict was already computed when the snapshot was created/refreshed.
- Simpler, avoids duplicating VerdictEngine logic.
- The AsyncIngestor (02:00) refreshes snapshots. If it also stores new verdicts (via `ScreeningService`), the monitor (04:00) just reads the latest verdict.

**Option B: Re-evaluate from raw snapshot data.**
- Load `company_snapshots.snapshot_data` JSONB, parse via `SnapshotDataParser`, call `VerdictEngine.evaluate()`.
- More resilient (handles cases where the ingestor updated snapshot but didn't create a new verdict row).
- More complex, requires importing VerdictEngine.

**Recommendation:** Use Option A. The AsyncIngestor already calls `ScreeningService` which creates verdicts. The monitor just compares `previous verdict status` vs `latest verdict status`. If the ingestor runs but verdict wasn't created for some reason, the freshness model handles it (stale data = INCOMPLETE confidence, which the monitor treats as transient).

### Flyway Migration Notes

**Migration `V20260318_003__add_watchlist_verdict_columns.sql`:**
```sql
-- Add denormalized verdict columns to watchlist_entries
ALTER TABLE watchlist_entries
    ADD COLUMN last_verdict_status VARCHAR(20),
    ADD COLUMN last_checked_at TIMESTAMPTZ;

-- Backfill from latest verdicts (one-time migration)
UPDATE watchlist_entries we
SET last_verdict_status = sub.status,
    last_checked_at = sub.created_at
FROM (
    SELECT DISTINCT ON (v.tenant_id, v.tax_number)
        v.tenant_id, v.tax_number, v.status, v.created_at
    FROM verdicts v
    ORDER BY v.tenant_id, v.tax_number, v.created_at DESC
) sub
WHERE we.tenant_id = sub.tenant_id
  AND we.tax_number = sub.tax_number;
```

**Naming convention:** `V{YYYYMMDD}_{NNN}__description.sql`. Use `V20260318_003` (follows `_001` for watchlist table, `_002` for company_name column).

### Previous Story Intelligence (Stories 3.5 and 3.6)

**From Story 3.5 (AsyncIngestor):**
- `AsyncIngestorHealthState` uses `AtomicReference<RunSnapshot>` for thread-safe health reporting — copy this pattern for `WatchlistMonitorHealthState`.
- Scheduling is disabled in test profile via `cron: "-"` in `application-test.yml` — do the same for WatchlistMonitor.
- `maskTaxNumber()` utility shows max 4 digits + `****` — reuse or extract to shared utility in `core.util`.
- `TenantContext.setCurrentTenant()` / `TenantContext.clear()` pattern in finally block — copy exactly.
- The ingestor calls `notificationService.getMonitoredPartners()` which returns `List<WatchlistPartner>` — extend this to include `lastVerdictStatus` for comparison.

**From Story 3.6 (Watchlist CRUD):**
- `NotificationRepository.findByTenantId()` has a lateral join on `verdicts`/`company_snapshots` — this is the tech debt that this story resolves by using denormalized columns instead.
- `WatchlistEntry` domain record exists in `notification.domain` — may need to add `lastVerdictStatus`/`lastCheckedAt` fields.
- `WatchlistEntryResponse` DTO already has `currentVerdictStatus` and `lastCheckedAt` fields — the DTO shape should not change (frontend compatibility preserved).
- Story 3.6 Debug Log notes: "verdict enrichment returns null for now. Will be populated via PartnerStatusChanged events in Story 3.7" — this is exactly what we're implementing.
- `AddResult.duplicate` flag propagated through API — no changes needed for add flow.

### Git Intelligence (Recent Commits)

- Recent commits are deployment/CI fixes and Story 3.5/3.6 code review resolutions.
- 343+ backend tests and 398 frontend tests pass as of Story 3.6 completion.
- No architectural changes that impact this story.
- `@EnableScheduling` is already on `RiskGuardApplication` (added in Story 3.5).

### Key Configuration

```yaml
# application.yml
risk-guard:
  watchlist-monitor:
    cron: "${WATCHLIST_MONITOR_CRON:0 0 4 * * ?}"    # 04:00 UTC daily (after AsyncIngestor at 02:00)
    delay-between-evaluations-ms: "${WATCHLIST_MONITOR_DELAY_MS:200}"

# application-test.yml
risk-guard:
  watchlist-monitor:
    cron: "-"                     # Disabled in tests
    delay-between-evaluations-ms: 0
```

### Project Structure Notes

- **Module boundaries (actual):** `WatchlistMonitor` in `screening.domain` (alongside `AsyncIngestor`); `PartnerStatusChangedListener` in `notification.domain`. Health state/indicator in `core.config` (shared infrastructure). `PartnerStatusChanged` event in `core.events` (shared). No new modules created.
- **Cross-module dependency (actual):** `screening.domain.WatchlistMonitor` calls `notification.domain.NotificationService` facade for watchlist data. `PartnerStatusChanged` event lives in `core.events` (moved from `screening.domain.events` to break module cycle).
- **Table ownership:** `notification` module owns `watchlist_entries` — the new columns and updates are within module boundaries. The backfill migration reads `verdicts` (screening-owned) — this is a one-time migration, not runtime code, so it's acceptable.
- **jOOQ note:** Continue using raw `field()` and `table()` references for `watchlist_entries` as established in Stories 3.5 and 3.6. Add TODO markers for future type-safe replacement.
- **No new frontend pages or components.** The WatchlistTable already displays verdict status and last checked timestamp. The only frontend change is verifying that the existing DTO shape (`WatchlistEntryResponse`) continues to work with the denormalized data source.
- **PartnerStatusChanged event reuse:** The event is already defined in `screening.domain.events` and published by `ScreeningService.search()`. This story adds WatchlistMonitor as a second publisher and PartnerStatusChangedListener as the first consumer.

### Key Files to Create or Modify

| File | Action | Notes |
|---|---|---|
| `backend/.../notification/domain/WatchlistMonitor.java` | **Create** | @Scheduled monitoring orchestrator |
| `backend/.../notification/domain/PartnerStatusChangedListener.java` | **Create** | @ApplicationModuleListener for event consumption |
| `backend/.../core/config/WatchlistMonitorHealthState.java` | **Create** | Thread-safe AtomicReference health state |
| `backend/.../core/config/WatchlistMonitorHealthIndicator.java` | **Create** | Spring Boot HealthIndicator |
| `backend/.../core/config/RiskGuardProperties.java` | **Modify** | Add WatchlistMonitor inner class |
| `backend/src/main/resources/application.yml` | **Modify** | Add watchlist-monitor config block |
| `backend/src/test/resources/application-test.yml` | **Modify** | Disable watchlist-monitor cron |
| `backend/src/main/resources/db/migration/V20260318_003__add_watchlist_verdict_columns.sql` | **Create** | Add + backfill denormalized columns |
| `backend/.../notification/domain/NotificationService.java` | **Modify** | Extend getMonitoredPartners() return type |
| `backend/.../notification/internal/NotificationRepository.java` | **Modify** | Add updateVerdictStatus(), remove lateral join, add findByTaxNumber() |
| `backend/.../notification/domain/WatchlistEntry.java` | **Modify** | Add lastVerdictStatus, lastCheckedAt fields |
| `backend/.../screening/domain/ScreeningService.java` | **Modify** | Add getLatestSnapshotWithVerdict() facade method |
| `backend/.../screening/internal/ScreeningRepository.java` | **Modify** | Add findLatestVerdictByTenantAndTax() query |
| `backend/src/test/java/.../notification/domain/WatchlistMonitorTest.java` | **Create** | Unit tests |
| `backend/src/test/java/.../notification/domain/PartnerStatusChangedListenerTest.java` | **Create** | Unit tests |
| `backend/src/test/java/.../core/config/WatchlistMonitorHealthIndicatorTest.java` | **Create** | Unit tests |
| `backend/src/test/java/.../notification/domain/NotificationServiceTest.java` | **Modify** | Update for changed getMonitoredPartners return type |

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` Story 3.7] — Story definition: "24h Background Monitoring Cycle", acceptance criteria (FR6)
- [Source: `_bmad-output/planning-artifacts/architecture.md` #notification module] — `WatchlistMonitor` (@Scheduled 24h cycle), `notification_outbox`, module boundary rules
- [Source: `_bmad-output/planning-artifacts/architecture.md` #Communication Patterns] — Facade call for cross-module data, ApplicationEvent for broadcasting
- [Source: `_bmad-output/planning-artifacts/architecture.md` #Cross-Module Cascade Safeguards] — "NAV API fails → Watchlist diff misses change" → logs "data source unavailable" as explicit event
- [Source: `_bmad-output/planning-artifacts/architecture.md` #notification Module Tables] — `watchlist_entries` schema with `last_verdict_status`, `last_checked_at`
- [Source: `_bmad-output/planning-artifacts/architecture.md` #Module-Level Failure Mode — notification] — Alert storms: digest mode if >5 alerts; rate limit 10 alerts/day/tenant
- [Source: `_bmad-output/implementation-artifacts/3-5-async-nav-debt-ingestor-background-data-freshness.md`] — AsyncIngestor pattern: scheduling, TenantContext, health state, rate limiting
- [Source: `_bmad-output/implementation-artifacts/3-6-watchlist-management-crud.md`] — Watchlist CRUD: NotificationService API, lateral join tech debt, WatchlistEntry domain type
- [Source: `_bmad-output/implementation-artifacts/3-6-watchlist-management-crud.md` #Completion Notes] — "verdict enrichment returns null — will be populated via PartnerStatusChanged events in Story 3.7"
- [Source: `_bmad-output/project-context.md`] — Module Facade rule, DTOs as records, @LogSafe, tenant isolation
- [Source: `backend/src/main/java/hu/riskguard/screening/domain/events/PartnerStatusChanged.java`] — Existing event: verdictId, tenantId, previousStatus, newStatus, timestamp
- [Source: `backend/src/main/java/hu/riskguard/screening/domain/AsyncIngestor.java`] — Reference pattern for scheduling, TenantContext, health state recording
- [Source: `backend/src/main/java/hu/riskguard/core/config/AsyncIngestorHealthState.java`] — AtomicReference<RunSnapshot> pattern to copy
- [Source: `backend/src/main/java/hu/riskguard/screening/domain/VerdictEngine.java`] — Pure function verdict evaluation (TAX_SUSPENDED > UNAVAILABLE > AT_RISK > INCOMPLETE > RELIABLE)
- [Source: `backend/src/main/resources/risk-guard-tokens.json`] — freshThresholdHours=6, staleThresholdHours=24, unavailableThresholdHours=48, maxAlertsPerDayPerTenant=10

## Dev Agent Record

### Agent Model Used

gitlab/duo-chat-opus-4-6

### Debug Log References

- Modulith circular dependency: `notification → screening → notification` cycle resolved by moving `WatchlistMonitor` to `screening.domain` (same location as `AsyncIngestor`) and `PartnerStatusChanged` event to `core.events` (shared module).
- `PartnerStatusChanged` event extended with `taxNumber` field for watchlist entry lookup by `PartnerStatusChangedListener`.

### Completion Notes List

- All 17 story tasks (BE-1 through BE-11, FE-1, FE-2, TEST-1 through TEST-4) completed.
- 360 backend tests pass, 399 frontend tests pass (0 failures).
- Lateral join tech debt from Story 3.6 resolved — `NotificationRepository.findByTenantId()` now reads denormalized columns directly.
- `WatchlistMonitor` placed in `screening.domain` (not `notification.domain` as originally planned) to avoid Spring Modulith circular dependency. Both background jobs (`AsyncIngestor`, `WatchlistMonitor`) now live together in screening module.
- `PartnerStatusChanged` moved from `screening.domain.events` to `core.events` to break module cycle.
- Implementation follows Option A (recommended): reads latest verdict from screening facade, no re-computation via VerdictEngine.
- ✅ Resolved review finding [HIGH]: H1 — Fixed stale @link references to WatchlistMonitor in PartnerStatusChanged.java and WatchlistMonitorHealthState.java
- ✅ Resolved review finding [HIGH]: H2 — Updated notification/package-info.java with accurate cross-module dependencies
- ✅ Resolved review finding [HIGH]: H3 — Updated WatchlistController.java Javadoc to reflect implemented verdict enrichment
- ✅ Resolved review finding [MEDIUM]: M1 — Added null JWT guards to IdentityController.updateLanguage() and getMandates()
- ✅ Resolved review finding [MEDIUM]: M2 — Fixed SnapshotVerdictResult.transientFailure() to detect INCOMPLETE/UNAVAILABLE statuses
- ✅ Resolved review finding [MEDIUM]: M3 — Extracted maskTaxNumber() to shared PiiUtil in core.util (DRY)
- ✅ Resolved review finding [MEDIUM]: M4 — Corrected File List path for risk-guard-tokens.json
- ✅ Resolved review finding [LOW]: L1 — Updated Dev Notes architecture section for actual WatchlistMonitor placement
- ✅ Resolved review finding [LOW]: L2 — Updated NotificationRepository.WatchlistEntryRecord Javadoc
- ✅ Resolved review finding [LOW]: L3 — Fixed File List for LocaleSwitcherLight.vue (non-existent) and added undocumented changes
- ✅ Resolved review finding R2 [HIGH]: R2-H1 — Updated core/events/package-info.java event catalog (stale entry + wrong PII policy)
- ✅ Resolved review finding R2 [HIGH]: R2-H2 — Sanitized WatchlistMonitor exception logging (PII leakage prevention)
- ✅ Resolved review finding R2 [MEDIUM]: R2-M1 — Eliminated double-write on status change (monitor + listener both writing)
- ✅ Resolved review finding R2 [MEDIUM]: R2-M2 — Added verdict columns to findByTenantIdAndTaxNumber() duplicate query

---

## Bugfixes Found During Manual Testing

The following bugs were discovered by the user during hands-on testing of Story 3.7.
These are **separate from the AC tasks** and address pre-existing issues or regressions exposed by this story's changes.

### BUGFIX-1: Watchlist company name not displayed (regression from BE-10)

**Symptom:** Company name column was blank for all watchlist entries on the UI.
**Root cause:** The Story 3.6 `NotificationRepository.findByTenantId()` used a lateral join that extracted `companyName` from `company_snapshots.snapshot_data` JSONB as a fallback. BE-10 replaced the lateral join with a direct column read, but `company_name` was never populated at insert time (`WatchlistController` passed `null`).
**Fix (3 parts):**
- Backend: `AddWatchlistEntryRequest` now accepts `companyName`; controller passes it through
- Frontend: `VerdictCard` and `WatchlistAddDialog` send `companyName` with the add request
- Migration `V20260319_001__backfill_watchlist_company_names.sql`: backfills existing rows from `snapshot_data` JSONB (iterates adapter keys generically)

### BUGFIX-2: Watchlist "Add Partner" throws silent error — `useApi()` composable crash

**Symptom:** Adding a partner from the Watchlist page showed "An error occurred" toast. No HTTP request was sent to the backend. Browser console showed: `SyntaxError: Must be called at the top of a setup function`.
**Root cause:** `watchlist.ts` Pinia store called `useApi()` inside async action methods. `useApi()` internally calls `useI18n()` which requires Vue's setup context. Pinia options-style actions execute outside setup context when triggered by user clicks.
**Fix:** Replaced `useApi()` with direct `$fetch` calls in `watchlist.ts` (matching the pattern used by `screening.ts`). The global `$fetch` interceptor in `api-locale.ts` handles `Accept-Language` and `credentials` automatically.

### BUGFIX-3: Verdict status and last_checked_at empty on newly added watchlist entries

**Symptom:** After adding a partner, verdict status and last checked columns were empty until page refresh.
**Root cause:** `addToWatchlist()` inserted the row without populating `last_verdict_status`/`last_checked_at`. The `PartnerStatusChangedListener` updates these asynchronously, but the `fetchEntries()` re-fetch happened before the listener completed.
**Fix:** `AddWatchlistEntryRequest` now accepts `verdictStatus`. `NotificationService.addToWatchlist()` populates denormalized verdict columns immediately after insert. Frontend passes `verdictStatus` from the search result.

### BUGFIX-4: 401 on expired token shows generic error instead of redirecting to login

**Symptom:** After Google OAuth token expired (~1h), API calls failed with 401 but the UI stayed on the authenticated page showing "An error occurred" toasts.
**Root cause:** No global 401 response interceptor existed. API calls threw errors caught by individual component catch blocks, which showed generic toasts.
**Fix:** Added `onResponseError` handler in `api-locale.ts` plugin's global `$fetch` interceptor. On 401 (excluding `/auth/` and `/identity/` endpoints), clears auth state and redirects to login. The `/identity/` exclusion prevents redirect loops during `initializeAuth()`.

### BUGFIX-5: JWT expiry too short — 1 hour forced re-login during work

**Symptom:** Users were logged out every hour, losing work context.
**Root cause:** `jwt-expiration-ms` was set to `3600000` (1 hour) in all environments.
**Fix:** Bumped to `86400000` (24 hours) in `application.yml`, `application-staging.yml`, and `application-prod.yml`. Created backlog Story 3.13 for proper refresh token rotation as the long-term solution.

### BUGFIX-6: Flash-of-login-page on hard refresh (F5)

**Symptom:** On hard refresh of any authenticated page, the login page flashed for ~1 second, then briefly the landing page, then the correct page loaded.
**Root cause (login flash):** The auth middleware called `initializeAuth()` asynchronously, and during the await, Nuxt rendered page content. The SSR landing page was served as fallback HTML for non-SSR routes.
**Root cause (landing page flash):** Nuxt's default SSR mode rendered the landing page on the server for all routes as a fallback.
**Fix:**
- Set `ssr: false` globally in `nuxt.config.ts` (SPA mode). Only `/` and `/company/**` override with SSR/ISR for SEO.
- Removed unnecessary `initializeAuth()` call from landing page `onMounted` — the middleware handles auth for protected routes.

### BUGFIX-7: Landing page not accessible — not in public routes list

**Symptom:** Navigating to `http://localhost:3000/` always redirected to `/auth/login`.
**Root cause:** `/` was not listed in `publicRoutes` in `risk-guard-tokens.json`. The `startsWith()` matching would have made all routes public if `/` was added naively.
**Fix:** Added `/` and `/company` to `publicRoutes`. Changed middleware to use exact match for `/` and prefix match for all other public routes.

### BUGFIX-8: Landing page health check shows "Service temporarily unavailable"

**Symptom:** Landing page showed "Service temporarily unavailable" next to the search bar even though the backend was running.
**Root cause:** The health check called `/actuator/health` which returned 503 (overall status `DOWN` due to `databaseTls` check failing on local Postgres without SSL). `$fetch` treated 503 as an error.
**Fix:** Added `ignoreResponseError: true` to the health check `$fetch` call. Any HTTP response (even 503) means the backend is reachable. Only network-level failures trigger the unavailable state.

### BUGFIX-9: Backend NPE on `/me` endpoint when no JWT present

**Symptom:** Repeated `NullPointerException: Cannot invoke "Jwt.getSubject()" because "jwt" is null` in backend logs.
**Root cause:** Landing page's `onMounted` called `initializeAuth()` → `fetchMe()` for every guest visit. The `/me` endpoint didn't guard against null `@AuthenticationPrincipal Jwt` — Spring Security let the request through without a principal due to the custom authentication entry point.
**Fix (2 parts):**
- Backend: Added null check on `jwt` parameter in `IdentityController.me()` — returns clean 401.
- Frontend: Removed `initializeAuth()` call from landing page `onMounted`. Only checks `authStore.isAuthenticated` (already set if user navigated from an authenticated route).

### BUGFIX-10: Landing page search button redirected to login instead of screening

**Symptom:** Clicking "Start screening" on the landing page navigated to `/auth/login?redirect=/screening/...` instead of performing the search.
**Root cause:** `LandingSearchBar.vue` hardcoded `navigateTo('/auth/login?redirect=...')` — contradicting the "no registration required" tagline.
**Fix:** Changed to `navigateTo('/screening/{taxNumber}')`. The auth middleware handles login redirect if needed. Updated 2 test expectations.

### BUGFIX-11: No language switcher on landing page

**Symptom:** Landing page was displayed in the default locale with no way to switch language.
**Root cause:** The `public.vue` layout had no language switcher component. The existing `LocaleSwitcher.vue` was styled for the dark sidebar.
**Fix:** Added `variant` prop to existing `LocaleSwitcher.vue` supporting a light-themed variant for the public layout header (no auth dependency in light mode).

---

### Change Log

- **2026-03-19 (AM):** Story 3.7 AC implementation complete. 17 tasks done, all tests green.
- **2026-03-19 (PM):** Manual testing by user revealed 11 bugs (BUGFIX-1 through BUGFIX-11). All fixed, all tests green: 360 backend, 399 frontend.
- **2026-03-19 (PM):** Senior developer code review: 3 HIGH, 4 MEDIUM, 3 LOW findings. 10 action items created under "Review Follow-ups (AI)".
- **2026-03-19 (PM):** Addressed code review findings — 10 items resolved (3H/4M/3L). Key changes: stale Javadoc fixes, null JWT guards, transient failure detection activated, maskTaxNumber DRY extraction, File List corrections.
- **2026-03-19 (PM):** Senior developer code review round 2: 2 HIGH, 2 MEDIUM, 2 LOW findings. 4 items fixed (2H/2M). Key changes: event catalog updated, PII-safe exception logging, double-write elimination, duplicate query verdict columns.

### File List

**Created (Story 3.7 ACs):**
- `backend/src/main/resources/db/migration/V20260318_003__add_watchlist_verdict_columns.sql` — AC3
- `backend/src/main/java/hu/riskguard/screening/domain/WatchlistMonitor.java` — AC1, AC2, AC5, AC6, AC7
- `backend/src/main/java/hu/riskguard/notification/domain/PartnerStatusChangedListener.java` — AC4
- `backend/src/main/java/hu/riskguard/notification/domain/MonitoredPartner.java` — AC2
- `backend/src/main/java/hu/riskguard/core/config/WatchlistMonitorHealthState.java` — AC8
- `backend/src/main/java/hu/riskguard/core/config/WatchlistMonitorHealthIndicator.java` — AC8
- `backend/src/main/java/hu/riskguard/core/events/PartnerStatusChanged.java` — AC2, AC4 (moved from screening.domain.events)
- `backend/src/test/java/hu/riskguard/screening/domain/WatchlistMonitorTest.java` — AC9
- `backend/src/test/java/hu/riskguard/core/config/WatchlistMonitorHealthIndicatorTest.java` — AC9
- `backend/src/test/java/hu/riskguard/notification/domain/PartnerStatusChangedListenerTest.java` — AC9

**Created (Bugfixes):**
- `backend/src/main/resources/db/migration/V20260319_001__backfill_watchlist_company_names.sql` — BUGFIX-1
- `_bmad-output/implementation-artifacts/3-13-refresh-token-rotation-silent-renewal.md` — BUGFIX-5 (backlog story)

**Created (Review Follow-ups):**
- `backend/src/main/java/hu/riskguard/core/util/PiiUtil.java` — M3 (shared maskTaxNumber utility)

**Modified (Review Follow-ups):**
- `backend/src/main/java/hu/riskguard/core/events/PartnerStatusChanged.java` — H1 (fixed stale @link)
- `backend/src/main/java/hu/riskguard/core/config/WatchlistMonitorHealthState.java` — H1 (fixed stale @link)
- `backend/src/main/java/hu/riskguard/notification/package-info.java` — H2 (updated cross-module deps)
- `backend/src/main/java/hu/riskguard/notification/api/WatchlistController.java` — H3 (updated Javadoc)
- `backend/src/main/java/hu/riskguard/identity/api/IdentityController.java` — M1 (null JWT guards), BUGFIX-9
- `backend/src/main/java/hu/riskguard/screening/domain/ScreeningService.java` — M2 (transient failure detection), AC1
- `backend/src/main/java/hu/riskguard/screening/domain/AsyncIngestor.java` — M3 (PiiUtil extraction)
- `backend/src/main/java/hu/riskguard/screening/domain/WatchlistMonitor.java` — M3 (PiiUtil extraction), R2-H2 (PII-safe logging), R2-M1 (double-write fix)
- `backend/src/main/java/hu/riskguard/notification/internal/NotificationRepository.java` — L2 (Javadoc fix), AC3, AC4, R2-M2 (verdict columns in duplicate query)
- `backend/src/main/java/hu/riskguard/core/events/package-info.java` — R2-H1 (event catalog updated)
- `backend/src/test/java/hu/riskguard/screening/domain/WatchlistMonitorTest.java` — R2-M1 (test updated for double-write fix)

**Modified (Story 3.7 ACs):**
- `backend/src/main/java/hu/riskguard/core/config/RiskGuardProperties.java` — AC7 (WatchlistMonitor config)
- `backend/src/main/java/hu/riskguard/notification/domain/NotificationService.java` — AC1, AC4 (facade methods)
- `backend/src/main/java/hu/riskguard/notification/internal/NotificationRepository.java` — AC3, AC4, AC8 (denorm columns, lateral join removed)
- `backend/src/main/java/hu/riskguard/screening/domain/ScreeningService.java` — AC1 (getLatestSnapshotWithVerdict facade)
- `backend/src/main/java/hu/riskguard/screening/internal/ScreeningRepository.java` — AC1 (findLatestVerdictByTenantAndTaxNumber)
- `backend/src/test/resources/application-test.yml` — AC7 (watchlist-monitor cron disabled)
- `backend/src/test/java/hu/riskguard/screening/ScreeningServiceIntegrationTest.java` — AC9 (PartnerStatusChanged import)
- `frontend/app/i18n/en/notification.json` — FE-2 (i18n keys)
- `frontend/app/i18n/hu/notification.json` — FE-2 (i18n keys)
- `start-local.sh` — Dev convenience (WATCHLIST_MONITOR_CRON every 60s for testing)

**Modified (Bugfixes):**
- `backend/src/main/java/hu/riskguard/notification/api/dto/AddWatchlistEntryRequest.java` — BUGFIX-1, BUGFIX-3 (companyName, verdictStatus)
- `backend/src/main/java/hu/riskguard/notification/api/WatchlistController.java` — BUGFIX-1, BUGFIX-3
- `backend/src/main/java/hu/riskguard/identity/api/IdentityController.java` — BUGFIX-9 (null JWT guard)
- `backend/src/main/resources/application.yml` — BUGFIX-5 (JWT 24h), AC7 (watchlist-monitor config)
- `backend/src/main/resources/application-staging.yml` — BUGFIX-5 (JWT 24h)
- `backend/src/main/resources/application-prod.yml` — BUGFIX-5 (JWT 24h)
- `backend/src/test/java/hu/riskguard/notification/api/WatchlistControllerTest.java` — BUGFIX-1, BUGFIX-3
- `backend/src/test/java/hu/riskguard/notification/domain/NotificationServiceTest.java` — BUGFIX-3
- `frontend/app/stores/watchlist.ts` — BUGFIX-2 (replaced useApi with $fetch)
- `frontend/app/plugins/api-locale.ts` — BUGFIX-4 (401 interceptor), BUGFIX-9 (/identity/ exclusion)
- `frontend/app/middleware/auth.global.ts` — BUGFIX-6, BUGFIX-7 (auth flow, public routes matching)
- `frontend/app/pages/index.vue` — BUGFIX-8, BUGFIX-9 (health check, removed initializeAuth)
- `frontend/app/pages/watchlist/index.vue` — BUGFIX-3 (verdictStatus in handleAddPartner)
- `frontend/app/components/Screening/VerdictCard.vue` — BUGFIX-1, BUGFIX-3 (companyName, verdictStatus)
- `frontend/app/components/Watchlist/WatchlistAddDialog.vue` — BUGFIX-1, BUGFIX-3 (emit companyName, verdictStatus)
- `frontend/app/components/Landing/LandingSearchBar.vue` — BUGFIX-10 (navigate to screening)
- `frontend/app/components/Landing/LandingSearchBar.spec.ts` — BUGFIX-10 (updated test expectations)
- `frontend/app/stores/auth.ts` — BUGFIX-6 (initializeAuth error handling)
- `frontend/app/layouts/public.vue` — BUGFIX-11 (LocaleSwitcher light variant)
- `frontend/app/components/Common/LocaleSwitcher.vue` — BUGFIX-11 (added `variant` prop for light theme)
- `frontend/app/app.vue` — BUGFIX-6 (SPA mode layout adjustment)
- `frontend/nuxt.config.ts` — BUGFIX-6 (ssr: false)
- `backend/build.gradle` — dependency version alignment
- `frontend/types/api.d.ts` — BUGFIX-1, BUGFIX-3 (companyName, verdictStatus)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — Story 3.13 backlog entry

**Deleted:**
- `backend/src/main/java/hu/riskguard/screening/domain/events/PartnerStatusChanged.java` — moved to `core.events`
