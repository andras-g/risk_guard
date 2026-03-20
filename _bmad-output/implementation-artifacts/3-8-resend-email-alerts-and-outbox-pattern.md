# Story 3.8: Resend Email Alerts & Outbox Pattern

Status: done

## Story

As a User,
I want to receive an email notification the moment a partner's status changes,
so that I can take immediate business action.

## Acceptance Criteria

### AC1 — Notification Outbox Table Exists
**Given** a Flyway migration for the `notification_outbox` table,
**When** the migration runs,
**Then** the table matches the architecture schema: `id` (UUID PK), `tenant_id` (UUID NOT NULL FK), `user_id` (UUID FK), `type` (VARCHAR — ALERT/DIGEST/MONITORING_INTERRUPTED), `payload` (JSONB), `status` (VARCHAR — PENDING/SENT/FAILED), `retry_count` (INT DEFAULT 0), `next_retry_at` (TIMESTAMPTZ), `created_at` (TIMESTAMPTZ), `sent_at` (TIMESTAMPTZ),
**And** indexes exist on `(status, next_retry_at)` for the outbox processor query and `(tenant_id)` for per-tenant queries.

### AC2 — PartnerStatusChanged Event Creates Outbox Record
**Given** a `PartnerStatusChanged` event is published (by `WatchlistMonitor` or user-initiated search),
**When** the existing `PartnerStatusChangedListener` receives it and detects a genuine status change (e.g., RELIABLE to AT_RISK),
**Then** a new `notification_outbox` record is created with `type=ALERT`, `status=PENDING`, `retry_count=0`,
**And** the `payload` JSONB contains: `tenantId`, `taxNumber`, `companyName`, `previousStatus`, `newStatus`, `verdictId`, `changedAt`, `sha256Hash`,
**And** one outbox record is created per affected tenant (a tax number may be on multiple tenants' watchlists),
**And** the `user_id` is populated from the watchlist entry owner.

### AC3 — OutboxProcessor Sends Emails via Resend
**Given** one or more `notification_outbox` records with `status=PENDING` and `next_retry_at <= now`,
**When** the `OutboxProcessor` scheduled job triggers (default: every 60 seconds),
**Then** for each pending record, it resolves the recipient email from `users` table via `user_id`,
**And** it renders a localized (HU or EN based on `user.preferred_language`) email body using `messages_hu.properties` / `messages_en.properties` templates,
**And** it sends the email via `ResendEmailSender` using the Resend Java SDK (`com.resend:resend-java:4.13.0`),
**And** on success, the record is updated to `status=SENT`, `sent_at=now()`,
**And** on failure, `retry_count` is incremented and `next_retry_at` is set using exponential backoff: `now() + min(2^retry_count * 30s, 1 hour)`.

### AC4 — Email Content Includes Audit Proof SHA-256 Hash
**Given** a status change notification email being sent,
**When** the email body is rendered,
**Then** the email includes: company name, tax number (masked: first 4 digits + ****), previous status, new status, timestamp of change,
**And** the email includes the SHA-256 audit hash from the verdict for verification,
**And** the email includes the "Informational Purpose Only" liability disclaimer,
**And** the email subject line is localized: HU: "Partner allapotvaltozas: {companyName}" / EN: "Partner status changed: {companyName}".

### AC5 — Exponential Backoff and Max Retries
**Given** a failed email send attempt,
**When** the `OutboxProcessor` retries,
**Then** it uses exponential backoff: 30s, 60s, 120s, 240s, 480s, 960s, capped at 1 hour,
**And** after 5 failed attempts (`retry_count >= 5`), the record is updated to `status=FAILED`,
**And** a WARN-level log is emitted with masked tax number: `"Email delivery failed permanently tenant={tenantId} tax_number=1234**** retries=5"`,
**And** failed notifications are visible in future admin UI (not built in this story).

### AC6 — Alert Storm Protection (Digest Mode)
**Given** the `OutboxProcessor` is creating outbox records for a tenant,
**When** the count of PENDING+SENT records for that tenant in the current day exceeds `maxAlertsPerDayPerTenant` (default: 10 from `risk-guard-tokens.json`),
**Then** instead of creating individual ALERT records, a single DIGEST record is created (or updated),
**And** the digest `payload` aggregates all status changes: array of `{taxNumber, companyName, previousStatus, newStatus}`,
**And** the digest email lists all changes in a summary table format,
**And** this prevents alert storms when many partners change status simultaneously.

### AC7 — Resend Configuration and API Key Management
**Given** the application configuration,
**When** the Resend email sender initializes,
**Then** the Resend API key is read from environment variable `RESEND_API_KEY` (already in `.env.example`),
**And** the sender email address is configurable via `risk-guard.email.from` property (default: `alerts@riskguard.hu`),
**And** if `RESEND_API_KEY` is not set or empty, the `ResendEmailSender` logs a WARN at startup: `"Resend API key not configured — email delivery disabled"`,
**And** outbox records are still created (for future delivery) but the processor skips sending and logs at DEBUG level.

### AC8 — Demo Mode Validates Infrastructure Without Sending
**Given** `riskguard.data-source.mode=demo`,
**When** the `OutboxProcessor` triggers,
**Then** it processes outbox records normally (reads, renders templates, updates status),
**But** instead of calling the Resend API, it logs the email content at INFO level: `"[DEMO] Email would be sent to={email} subject={subject}"`,
**And** the record is marked as `SENT` (infrastructure validated without actual email delivery),
**And** this behavior is controlled by a `risk-guard.email.enabled` property (default: `true` in prod, `false` in demo/test).

### AC9 — Health Indicator Reports Outbox Status
**Given** the Spring Boot Actuator is enabled,
**When** `GET /actuator/health` is called,
**Then** the response includes an `outboxProcessor` component with:
  - `status: UP` (processor running, or never run yet)
  - `lastRun`: ISO-8601 timestamp of last completed run, or `"never"`
  - `pendingCount`: current count of PENDING outbox records
  - `failedCount`: current count of FAILED outbox records
  - `lastEmailsSent`: count of emails sent in last run

### AC10 — No Regressions
**Given** the new OutboxProcessor, ResendEmailSender, migration, and listener changes,
**When** `./gradlew check` and frontend tests are run,
**Then** all existing tests pass with zero regressions,
**And** new unit tests cover: (a) outbox record creation on status change; (b) OutboxProcessor happy path (PENDING to SENT); (c) exponential backoff on failure; (d) max retry exceeded (PENDING to FAILED); (e) digest mode activation; (f) demo mode skip; (g) missing API key graceful degradation; (h) email content includes SHA-256 hash; (i) localized email rendering (HU and EN); (j) health indicator states.

## Tasks / Subtasks

### Backend Tasks

- [x] **BE-1:** Add Resend Java SDK dependency to `backend/build.gradle` — `implementation 'com.resend:resend-java:4.13.0'`. Remove unused `spring-boot-starter-mail` if no other consumer exists (Resend SDK uses its own HTTP client, not JavaMailSender). (AC3, AC7)
- [x] **BE-2:** Create Flyway migration `V20260320_001__create_notification_outbox.sql` — creates `notification_outbox` table per architecture schema (AC1). Columns: `id` UUID PK, `tenant_id` UUID NOT NULL FK, `user_id` UUID FK, `type` VARCHAR(30) NOT NULL, `payload` JSONB NOT NULL, `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING', `retry_count` INT NOT NULL DEFAULT 0, `next_retry_at` TIMESTAMPTZ, `created_at` TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, `sent_at` TIMESTAMPTZ. Indexes: `idx_outbox_status` on `(status, next_retry_at)`, `idx_outbox_tenant` on `(tenant_id)`. (AC1)
- [x] **BE-3:** Add email configuration to `RiskGuardProperties.java` — inner class `Email` with fields: `enabled` (boolean, default true), `from` (String, default `alerts@riskguard.hu`), `maxRetriesCount` (int, default 5), `baseBackoffSeconds` (int, default 30), `maxBackoffSeconds` (int, default 3600). (AC5, AC7, AC8)
- [x] **BE-4:** Add email config block to `application.yml`, `application-test.yml`, `application-staging.yml`, `application-prod.yml` — `risk-guard.email.enabled: ${EMAIL_ENABLED:true}`, `risk-guard.email.from: ${EMAIL_FROM:alerts@riskguard.hu}`. In test profile: `enabled: false`. Bind `RESEND_API_KEY` env var via `risk-guard.email.resend-api-key: ${RESEND_API_KEY:}`. (AC7, AC8)
- [x] **BE-5:** Create `ResendEmailSender.java` in `hu.riskguard.notification.internal` — Spring `@Component` wrapping Resend SDK. Constructor initializes `Resend` client from API key property. If key is blank, logs WARN and sets `available=false`. Method `send(String to, String subject, String htmlBody)` returns boolean success. Catches `ResendException`, logs error (no PII), returns false. When `available=false`, logs DEBUG and returns false without calling API. (AC3, AC7)
- [x] **BE-6:** Create `EmailTemplateRenderer.java` in `hu.riskguard.notification.internal` — Spring `@Component` that renders localized HTML email content. Takes `OutboxPayload` record + `Locale` (from user's `preferred_language`). Uses `MessageSource` (backed by `messages_hu.properties` / `messages_en.properties`) for template strings. Produces HTML with: company name, masked tax number (via `PiiUtil.maskTaxNumber()`), previous/new status with color-coded badges, change timestamp, SHA-256 hash, liability disclaimer. (AC4)
- [x] **BE-7:** Create `OutboxProcessor.java` in `hu.riskguard.notification.domain` — `@Component` with `@Scheduled` (property: `risk-guard.outbox.cron`, default: `0 */1 * * * ?` — every 60s). Queries PENDING records where `next_retry_at <= now` OR `next_retry_at IS NULL`. For each: resolves user email from `IdentityService.getUserEmail(userId)`, renders email via `EmailTemplateRenderer`, sends via `ResendEmailSender`. On success: update `status=SENT, sent_at=now()`. On failure: increment `retry_count`, compute next_retry_at with exponential backoff, update. If `retry_count >= maxRetriesCount`: update `status=FAILED`, log WARN. Records health state. (AC3, AC5, AC8)
- [x] **BE-8:** Create `OutboxHealthState.java` in `hu.riskguard.core.config` — thread-safe `AtomicReference<RunSnapshot>` pattern (copy from `AsyncIngestorHealthState`). Records: `lastRun`, `pendingCount`, `failedCount`, `lastEmailsSent`. (AC9)
- [x] **BE-9:** Create `OutboxHealthIndicator.java` in `hu.riskguard.core.config` — Spring Boot `HealthIndicator` reporting outbox processor status from `OutboxHealthState`. Always UP (processing failures are non-fatal to application health). (AC9)
- [x] **BE-10:** Add outbox repository methods to `NotificationRepository.java` — `insertOutboxRecord(...)`, `findPendingOutboxRecords(limit)` (status=PENDING, next_retry_at <= now OR null, ORDER BY created_at, LIMIT configurable), `updateOutboxSent(id)`, `updateOutboxRetry(id, retryCount, nextRetryAt)`, `updateOutboxFailed(id)`, `countPendingByTenant(tenantId)`, `countTodayAlertsByTenant(tenantId, startOfDay)`, `countPendingTotal()`, `countFailedTotal()`. (AC1, AC2, AC3, AC5, AC6)
- [x] **BE-11:** Modify `PartnerStatusChangedListener.java` — after updating watchlist entry verdict status (existing logic), check if status actually changed (previousStatus != newStatus). If changed: call `NotificationService.createAlertNotification(tenantId, userId, payload)` which handles digest-mode gating (AC6) and inserts outbox record (AC2). The listener is the SINGLE point of outbox record creation — the WatchlistMonitor and user search publish the event, the listener creates the outbox record.
- [x] **BE-12:** Add outbox creation methods to `NotificationService.java` facade — `createAlertNotification(tenantId, userId, OutboxPayload)`: checks daily alert count for tenant against `maxAlertsPerDayPerTenant` (from `RiskGuardProperties`). If under limit: inserts ALERT record. If at/over limit: creates or appends to DIGEST record. Also: `getOutboxStats()` returning pending/failed counts for health indicator. (AC2, AC6)
- [x] **BE-13:** Add `getUserEmail(UUID userId)` method to `IdentityService.java` facade — returns user's email address. The OutboxProcessor in notification module calls this via facade to resolve recipient. (AC3)
- [x] **BE-14:** Add `getUserPreferredLanguage(UUID userId)` method to `IdentityService.java` facade — returns user's `preferred_language` (hu/en). Used by `EmailTemplateRenderer` for localization. (AC4)
- [x] **BE-15:** Expand email templates in `messages_hu.properties` and `messages_en.properties` — add keys for: `email.subject.statusChange`, `email.subject.digest`, `email.body.header`, `email.body.statusChange.detail`, `email.body.digest.summary`, `email.body.auditHash`, `email.body.disclaimer`, `email.body.footer`. Hungarian templates must use proper Hungarian characters. (AC4)
- [x] **BE-16:** Add outbox processor config to `application.yml` and `application-test.yml` — `risk-guard.outbox.cron`, `risk-guard.outbox.batch-size` (default: 50). In test: cron disabled (`"-"`). (AC3)

### Frontend Tasks

- [x] **FE-1:** Add i18n keys to `hu/notification.json` and `en/notification.json` for email-related UI text — `notification.email.alertsEnabled`, `notification.email.digestMode`, `notification.watchlist.emailNotifications` (future UI hooks; keys should exist for consistency). (AC4)

### Testing Tasks

- [x] **TEST-1:** `OutboxProcessorTest.java` in `hu.riskguard.notification.domain` — unit tests covering: (a) happy path PENDING→SENT; (b) failure with retry (backoff calculation); (c) max retries exceeded PENDING→FAILED; (d) demo mode skips Resend call but marks SENT; (e) missing API key skips send; (f) batch processing respects limit; (g) empty queue is no-op. (AC10)
- [x] **TEST-2:** `ResendEmailSenderTest.java` in `hu.riskguard.notification.internal` — unit tests: (a) successful send returns true; (b) ResendException returns false; (c) missing API key returns false without calling SDK; (d) null/blank recipient returns false. (AC10)
- [x] **TEST-3:** `EmailTemplateRendererTest.java` in `hu.riskguard.notification.internal` — unit tests: (a) Hungarian locale renders HU template with correct placeholders; (b) English locale renders EN template; (c) SHA-256 hash is included; (d) tax number is masked; (e) disclaimer text is present. (AC10)
- [x] **TEST-4:** `OutboxHealthIndicatorTest.java` in `hu.riskguard.core.config` — unit tests: never run → UP with lastRun:never, clean run → UP with counts, has failed records → UP with non-zero failedCount. (AC10)
- [x] **TEST-5:** Update `PartnerStatusChangedListenerTest.java` — add tests for: (a) status change creates outbox record; (b) no status change (same status) does NOT create outbox record; (c) digest mode triggered when daily count exceeds threshold. (AC10)
- [x] **TEST-6:** Verify `./gradlew check` passes — all existing + new tests green. Confirm no regressions in WatchlistMonitor, ScreeningService, or NotificationService tests. (AC10)

### Review Follow-ups (AI)

- [x] [AI-Review][CRITICAL] **C1 — sha256Hash always null, breaking AC4**: `PartnerStatusChangedListener.java:108` hardcodes `payload.put("sha256Hash", null)` with a "for now" comment. `PartnerStatusChanged` event carries `verdictId` — look up the verdict record from the screening module (via facade) to retrieve the actual SHA-256 hash and populate it in the payload. Every email currently shows "N/A" for the audit proof hash. [PartnerStatusChangedListener.java:105-108]
- [x] [AI-Review][HIGH] **H1 — Digest mode counts only PENDING alerts, not PENDING+SENT**: `NotificationRepository.countTodayAlertsByTenant()` counts only `type=ALERT` without filtering by status. Per AC6, the limit should apply to PENDING+SENT combined (i.e., total alerts sent or queued today). Remove the status filter or add `.and(field("status").in("PENDING","SENT"))` — otherwise 9 SENT + 1 PENDING = 1 counted, allowing 9 more individual alerts before digest kicks in. [NotificationRepository.java:417-423]
- [x] [AI-Review][HIGH] **H2 — WARN log on permanent failure missing tenant and masked tax number per AC5**: `OutboxProcessor.handleRetry()` logs `"Email delivery failed permanently id={} retries={}"` but AC5 requires `"Email delivery failed permanently tenant={tenantId} tax_number=1234**** retries=5"`. Add `record.tenantId()` and `PiiUtil.maskTaxNumber(taxNumber)` (parsed from payload) to the WARN log. [OutboxProcessor.java:160]
- [x] [AI-Review][HIGH] **H3 — createAlertNotification and digest branching logic in NotificationService is entirely untested**: `NotificationServiceTest` only tests watchlist CRUD — zero tests for `createAlertNotification()`, `createOrAppendDigest()`, or `getOutboxStats()`. Add tests for: (a) under daily limit → ALERT record inserted; (b) at/over limit → DIGEST record created; (c) at/over limit with existing digest → digest payload appended; (d) JSON serialization failure is handled gracefully. [NotificationServiceTest.java]
- [x] [AI-Review][HIGH] **H4 — ResendEmailSender TEST-2(a)(b) marked [x] complete but not implemented**: `ResendEmailSenderTest` explicitly acknowledges that successful send and ResendException paths are not tested. WireMock is already in test dependencies (`wiremock-standalone:3.10.0`). Add WireMock-based tests: stub `POST https://api.resend.com/emails` → 200 response (success), stub → 422/500 (failure/ResendException). These are AC10 required test cases (a) and (b). [ResendEmailSenderTest.java]

## Dev Notes

### Architecture Fit

- **Module placement:** All new classes live in the `notification` module. `OutboxProcessor` in `notification.domain` (alongside `NotificationService`). `ResendEmailSender` and `EmailTemplateRenderer` in `notification.internal` (implementation details, not exposed cross-module). Health state/indicator in `core.config` (shared infrastructure pattern — matching `AsyncIngestorHealthState`, `WatchlistMonitorHealthState`).
- **Outbox pattern rationale:** The `notification_outbox` table guarantees at-least-once delivery. Notification is persisted FIRST (within the event listener's transaction context), then the `OutboxProcessor` polls and sends asynchronously. If the app crashes between persist and send, the record is picked up on next processor run. This is the standard transactional outbox pattern.
- **Event flow:** `WatchlistMonitor` (screening module) → publishes `PartnerStatusChanged` event → `PartnerStatusChangedListener` (notification module) receives event, updates watchlist entry verdict, AND creates outbox record → `OutboxProcessor` (notification module) polls outbox, sends via `ResendEmailSender` → Resend HTTP API delivers email.
- **Cross-module data access:** `OutboxProcessor` needs user email and preferred language. Per architecture communication matrix: **Need return value → facade call**. Add `getUserEmail()` and `getUserPreferredLanguage()` to `IdentityService` facade. Do NOT import `IdentityRepository` directly.
- **Table ownership:** `notification` module owns `notification_outbox` (declared in architecture). The outbox repository methods are added to the existing `NotificationRepository.java` which already declares `notification_outbox` in its scope Javadoc.

### Outbox Processor Implementation Pattern

```java
@Component
public class OutboxProcessor {

    @Scheduled(cron = "${risk-guard.outbox.cron:0 */1 * * * ?}")
    public void processOutbox() {
        List<OutboxRecord> pending = notificationRepository
            .findPendingOutboxRecords(properties.getOutbox().getBatchSize());
        int sent = 0, failed = 0;
        for (OutboxRecord record : pending) {
            try {
                String email = identityService.getUserEmail(record.userId());
                Locale locale = identityService.getUserPreferredLanguage(record.userId());
                String subject = templateRenderer.renderSubject(record, locale);
                String body = templateRenderer.renderBody(record, locale);

                if (!emailProperties.isEnabled()) {
                    log.info("[DEMO] Email would be sent to={} subject={}", 
                        PiiUtil.maskEmail(email), subject);
                    notificationRepository.updateOutboxSent(record.id());
                    sent++;
                    continue;
                }

                boolean success = resendEmailSender.send(email, subject, body);
                if (success) {
                    notificationRepository.updateOutboxSent(record.id());
                    sent++;
                } else {
                    handleRetry(record);
                    if (record.retryCount() + 1 >= maxRetries) failed++;
                }
            } catch (Exception e) {
                log.error("Outbox processing failed id={}", record.id(), e);
                handleRetry(record);
            }
        }
        healthState.recordRun(sent, failed, 
            notificationRepository.countPendingTotal(),
            notificationRepository.countFailedTotal());
    }

    private void handleRetry(OutboxRecord record) {
        int newRetryCount = record.retryCount() + 1;
        if (newRetryCount >= properties.getEmail().getMaxRetriesCount()) {
            notificationRepository.updateOutboxFailed(record.id());
            log.warn("Email delivery failed permanently id={} retries={}", 
                record.id(), newRetryCount);
        } else {
            long backoffSeconds = Math.min(
                (long) Math.pow(2, newRetryCount) * properties.getEmail().getBaseBackoffSeconds(),
                properties.getEmail().getMaxBackoffSeconds());
            OffsetDateTime nextRetry = OffsetDateTime.now().plusSeconds(backoffSeconds);
            notificationRepository.updateOutboxRetry(record.id(), newRetryCount, nextRetry);
        }
    }
}
```

### ResendEmailSender Implementation Pattern

```java
@Component
public class ResendEmailSender {
    private final Resend resend;
    private final boolean available;
    private final String fromAddress;

    public ResendEmailSender(RiskGuardProperties properties) {
        String apiKey = properties.getEmail().getResendApiKey();
        this.fromAddress = properties.getEmail().getFrom();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Resend API key not configured — email delivery disabled");
            this.resend = null;
            this.available = false;
        } else {
            this.resend = new Resend(apiKey);
            this.available = true;
        }
    }

    public boolean send(String to, String subject, String htmlBody) {
        if (!available) {
            log.debug("Resend not available, skipping email to={}", PiiUtil.maskEmail(to));
            return false;
        }
        try {
            CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromAddress)
                .to(to)
                .subject(subject)
                .html(htmlBody)
                .build();
            CreateEmailResponse response = resend.emails().send(params);
            log.info("Email sent via Resend id={}", response.getId());
            return true;
        } catch (ResendException e) {
            log.error("Resend API error: {}", e.getMessage());
            return false;
        }
    }
}
```

### Alert Storm Protection (Digest Mode)

Architecture specifies: digest mode if > 5 alerts in a single cycle, rate limit per-tenant max 10 individual alerts/day. Implementation:

1. `PartnerStatusChangedListener` calls `NotificationService.createAlertNotification()`
2. `NotificationService` counts today's ALERT records for this tenant via `countTodayAlertsByTenant(tenantId, startOfDay)`
3. If count < `maxAlertsPerDayPerTenant` (10): insert individual ALERT record
4. If count >= limit: look for existing PENDING DIGEST record for today. If found: append to its payload array. If not found: create new DIGEST record with aggregated payload.
5. The `OutboxProcessor` handles both ALERT and DIGEST types — ALERT sends individual email, DIGEST sends summary table email.

### Exponential Backoff Formula

`next_retry_at = now() + min(2^retry_count * base_backoff_seconds, max_backoff_seconds)`

| Retry | Delay | Cumulative |
|-------|-------|-----------|
| 1 | 60s | 1 min |
| 2 | 120s | 3 min |
| 3 | 240s | 7 min |
| 4 | 480s | 15 min |
| 5 | FAILED | — |

Default: `base_backoff_seconds=30`, `max_backoff_seconds=3600`. After 5 retries: permanently FAILED.

### Critical Anti-Patterns to Avoid

1. **DO NOT send emails synchronously in the event listener.** The outbox pattern exists to decouple event handling from email delivery. The listener creates the outbox record, the processor sends. This prevents event processing from being blocked by slow Resend API calls.
2. **DO NOT use `spring-boot-starter-mail` / `JavaMailSender`.** The architecture specifies Resend (HTTP API), not SMTP. Remove `spring-boot-starter-mail` from `build.gradle` and replace with `com.resend:resend-java:4.13.0`. The Resend SDK uses its own HTTP client internally.
3. **DO NOT log email addresses or full tax numbers.** Use `PiiUtil.maskTaxNumber()` (already in `core.util`) and add `PiiUtil.maskEmail()` (mask local part, keep domain).
4. **DO NOT import from `identity.internal`** — use `IdentityService` facade only for `getUserEmail()` and `getUserPreferredLanguage()`.
5. **DO NOT create a new event type for outbox records.** The outbox record creation happens IN the existing `PartnerStatusChangedListener`. No new application events needed.
6. **DO NOT set TenantContext in the OutboxProcessor.** The outbox records already contain `tenant_id`. The processor reads records cross-tenant (it's a system process, not tenant-scoped). SQL queries filter by record fields, not by TenantContext.
7. **DO NOT call `resend.emails().send()` when `email.enabled=false`.** In demo/test mode, log the email content and mark as SENT to validate the full pipeline without external API calls.

### Spring Boot 4 / Modulith Notes

- **Health package:** Use `org.springframework.boot.health.contributor.{Health, HealthIndicator, Status}` (moved in Spring Boot 4, learned in Stories 3.4, 3.5, 3.7).
- **Scheduler thread:** `@Scheduled` runs on the `taskScheduler` thread pool. AsyncIngestor (02:00), WatchlistMonitor (04:00), and OutboxProcessor (every 60s) share the scheduler. The outbox processor runs frequently but processes in batches (default 50), so each run is fast. No contention with the daily jobs.
- **Event listener transaction:** `PartnerStatusChangedListener` uses `@ApplicationModuleListener`. The outbox record INSERT happens in the same transaction context as the watchlist entry UPDATE. If the transaction fails, neither the watchlist update nor the outbox record is persisted — consistent state.
- **MessageSource for templates:** Spring Boot auto-configures `MessageSource` from `messages_*.properties`. Inject `MessageSource` into `EmailTemplateRenderer` and use `messageSource.getMessage(key, args, locale)`. For HTML templates, build the HTML string programmatically (no Thymeleaf dependency — keep it simple).

### Flyway Migration Notes

**Migration `V20260320_001__create_notification_outbox.sql`:**
```sql
CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID REFERENCES users(id),
    type VARCHAR(30) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_status ON notification_outbox (status, next_retry_at);
CREATE INDEX idx_outbox_tenant ON notification_outbox (tenant_id);
```

**Naming convention:** `V{YYYYMMDD}_{NNN}__description.sql`. Use `V20260320_001` (today's date, first migration).

### Previous Story Intelligence (Story 3.7)

**From Story 3.7 (24h Background Monitoring Cycle):**
- `WatchlistMonitor` publishes `PartnerStatusChanged` events when verdict changes — this is the PRIMARY trigger for email alerts.
- `PartnerStatusChangedListener` already exists and consumes events — extend it to create outbox records.
- `WatchlistMonitorHealthState` uses `AtomicReference<RunSnapshot>` for thread-safe health reporting — copy this pattern for `OutboxHealthState`.
- `PiiUtil.maskTaxNumber()` already exists in `core.util` — reuse for email content and logs.
- Scheduling is disabled in test profile via `cron: "-"` in `application-test.yml` — do the same for OutboxProcessor.
- Review finding R2-H2 established PII-safe exception logging — follow same pattern in `OutboxProcessor` and `ResendEmailSender`.

**From Story 3.6 (Watchlist CRUD):**
- `watchlist_entries` has `user_id` column — this links to the user who should receive the email notification.
- `AddWatchlistEntryRequest` includes `companyName` — this is available in the watchlist entry for email content.

### Git Intelligence (Recent Commits)

- Latest commit is `fe4aa70` — JWT algorithm fix, project-context.md update.
- 360+ backend tests and 399+ frontend tests pass as of Story 3.7 completion.
- No architectural changes that impact this story.
- `@EnableScheduling` is already on `RiskGuardApplication` (added in Story 3.5).
- `PiiUtil` was extracted to `core.util` in Story 3.7 review (finding M3) — ready to extend with `maskEmail()`.

### Key Configuration

```yaml
# application.yml
risk-guard:
  email:
    enabled: "${EMAIL_ENABLED:true}"
    from: "${EMAIL_FROM:alerts@riskguard.hu}"
    resend-api-key: "${RESEND_API_KEY:}"
    max-retries-count: 5
    base-backoff-seconds: 30
    max-backoff-seconds: 3600
  outbox:
    cron: "${OUTBOX_CRON:0 */1 * * * ?}"    # Every 60 seconds
    batch-size: "${OUTBOX_BATCH_SIZE:50}"

# application-test.yml
risk-guard:
  email:
    enabled: false
    resend-api-key: ""
  outbox:
    cron: "-"                     # Disabled in tests
    batch-size: 10
```

### Project Structure Notes

- **Module boundaries:** All new production classes within `notification` module. `OutboxProcessor` in `notification.domain` (business logic, scheduled job). `ResendEmailSender` and `EmailTemplateRenderer` in `notification.internal` (infrastructure, not exposed). Health state/indicator in `core.config` (shared pattern).
- **Cross-module dependencies:** `OutboxProcessor` calls `IdentityService` facade for user email/language (notification → identity via facade). `PartnerStatusChangedListener` already consumes `PartnerStatusChanged` from `core.events` (established in Story 3.7). No new cross-module dependencies introduced beyond the identity facade calls.
- **Table ownership:** `notification` module owns `notification_outbox` (architecture-defined). `NotificationRepository` already declares this table in its scope Javadoc — just adding the methods.
- **jOOQ note:** Continue using raw `field()` and `table()` references for `notification_outbox` as established in Stories 3.5, 3.6, and 3.7. Add TODO markers for future type-safe replacement.
- **No new frontend pages or components.** This story is backend-only for email delivery infrastructure. Future stories (3.9 Portfolio Pulse, 3.10 Flight Control) will add UI for notification history. Frontend changes are limited to adding i18n keys for future use.
- **Dependency change:** Replace `spring-boot-starter-mail` with `com.resend:resend-java:4.13.0` in `build.gradle`. Verify no other code imports from `org.springframework.mail` — if none, safe to remove.

### Key Files to Create or Modify

| File | Action | Notes |
|---|---|---|
| `backend/build.gradle` | **Modify** | Add `resend-java:4.13.0`, remove `spring-boot-starter-mail` |
| `backend/.../db/migration/V20260320_001__create_notification_outbox.sql` | **Create** | Outbox table + indexes (AC1) |
| `backend/.../notification/domain/OutboxProcessor.java` | **Create** | @Scheduled outbox polling + send orchestration (AC3, AC5, AC8) |
| `backend/.../notification/internal/ResendEmailSender.java` | **Create** | Resend SDK wrapper (AC3, AC7) |
| `backend/.../notification/internal/EmailTemplateRenderer.java` | **Create** | Localized HTML email rendering (AC4) |
| `backend/.../core/config/OutboxHealthState.java` | **Create** | Thread-safe AtomicReference health state (AC9) |
| `backend/.../core/config/OutboxHealthIndicator.java` | **Create** | Spring Boot HealthIndicator (AC9) |
| `backend/.../core/config/RiskGuardProperties.java` | **Modify** | Add Email + Outbox inner classes (AC5, AC7) |
| `backend/.../core/util/PiiUtil.java` | **Modify** | Add `maskEmail()` method |
| `backend/.../notification/domain/NotificationService.java` | **Modify** | Add `createAlertNotification()`, `getOutboxStats()` (AC2, AC6) |
| `backend/.../notification/domain/PartnerStatusChangedListener.java` | **Modify** | Add outbox record creation on status change (AC2) |
| `backend/.../notification/internal/NotificationRepository.java` | **Modify** | Add outbox CRUD methods (AC1-AC6) |
| `backend/.../identity/domain/IdentityService.java` | **Modify** | Add `getUserEmail()`, `getUserPreferredLanguage()` facade methods (AC3, AC4) |
| `backend/.../identity/internal/IdentityRepository.java` | **Modify** | Add `findEmailById()`, `findPreferredLanguageById()` queries |
| `backend/src/main/resources/application.yml` | **Modify** | Add email + outbox config block (AC7, AC8) |
| `backend/src/test/resources/application-test.yml` | **Modify** | Disable outbox cron, set email.enabled=false |
| `backend/src/main/resources/messages_hu.properties` | **Modify** | Expand email template keys (AC4) |
| `backend/src/main/resources/messages_en.properties` | **Modify** | Expand email template keys (AC4) |
| `backend/src/test/java/.../notification/domain/OutboxProcessorTest.java` | **Create** | Unit tests (AC10) |
| `backend/src/test/java/.../notification/internal/ResendEmailSenderTest.java` | **Create** | Unit tests (AC10) |
| `backend/src/test/java/.../notification/internal/EmailTemplateRendererTest.java` | **Create** | Unit tests (AC10) |
| `backend/src/test/java/.../core/config/OutboxHealthIndicatorTest.java` | **Create** | Unit tests (AC10) |
| `backend/src/test/java/.../notification/domain/PartnerStatusChangedListenerTest.java` | **Modify** | Add outbox creation tests (AC10) |
| `frontend/app/i18n/hu/notification.json` | **Modify** | Add email-related i18n keys |
| `frontend/app/i18n/en/notification.json` | **Modify** | Add email-related i18n keys |

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` Story 3.8] — Story definition: "Resend Email Alerts & Outbox Pattern", acceptance criteria (FR7)
- [Source: `_bmad-output/planning-artifacts/architecture.md` #notification module] — `OutboxProcessor` (outbox pattern retry scheduler), `ResendEmailSender` (Resend API integration), `notification_outbox` table schema
- [Source: `_bmad-output/planning-artifacts/architecture.md` #Module-Level Failure Mode — notification] — Resend API downtime: outbox pattern with retry. Alert storms: digest mode if >5 alerts. Rate limit 10 alerts/day/tenant. Outbox backpressure: max queue depth auto-switches to digest.
- [Source: `_bmad-output/planning-artifacts/architecture.md` #notification Module Tables] — `notification_outbox` schema: id, tenant_id, user_id, type, payload, status, retry_count, next_retry_at, created_at, sent_at
- [Source: `_bmad-output/planning-artifacts/architecture.md` #Cross-Module Cascade Safeguards] — "NAV API fails → Watchlist diff misses change → logs data source unavailable as explicit event"
- [Source: `_bmad-output/planning-artifacts/architecture.md` #Communication Patterns] — Facade call for cross-module data, ApplicationEvent for broadcasting
- [Source: `_bmad-output/planning-artifacts/architecture.md` #External Integration Points] — Resend Email API: `notification` module, `ResendEmailSender.java`, REST API
- [Source: `_bmad-output/implementation-artifacts/3-7-24h-background-monitoring-cycle.md`] — WatchlistMonitor publishes PartnerStatusChanged, PartnerStatusChangedListener pattern, health state/indicator pattern, PiiUtil, scheduling conventions
- [Source: `_bmad-output/implementation-artifacts/3-6-watchlist-management-crud.md`] — Watchlist CRUD: NotificationService API, watchlist_entries.user_id for recipient resolution
- [Source: `_bmad-output/project-context.md`] — Module Facade rule, DTOs as records, @LogSafe, tenant isolation, PII zero-tolerance
- [Source: `backend/src/main/java/hu/riskguard/core/events/PartnerStatusChanged.java`] — Event: verdictId, tenantId, taxNumber, previousStatus, newStatus, timestamp
- [Source: `backend/src/main/java/hu/riskguard/notification/domain/PartnerStatusChangedListener.java`] — Existing event listener to extend with outbox record creation
- [Source: `backend/src/main/java/hu/riskguard/core/config/AsyncIngestorHealthState.java`] — AtomicReference pattern to copy for OutboxHealthState
- [Source: `backend/src/main/java/hu/riskguard/core/util/PiiUtil.java`] — Shared maskTaxNumber utility, extend with maskEmail
- [Source: `backend/src/main/resources/messages_hu.properties`] — Existing placeholder email templates with "Story 3.8 will expand" comment
- [Source: `backend/.env.example`] — `RESEND_API_KEY=` placeholder already present
- [Source: `risk-guard-tokens.json`] — `rateLimits.maxAlertsPerDayPerTenant: 10` — digest mode threshold
- [Source: Resend Java SDK v4.13.0] — `com.resend:resend-java:4.13.0`, `CreateEmailOptions.builder()`, `resend.emails().send()`, `ResendException`

## Dev Agent Record

### Agent Model Used

duo-chat-opus-4-6 (gitlab/duo-chat-opus-4-6)

### Debug Log References

- Fixed DateTimeFormatter pattern `z` → `XXX` in EmailTemplateRenderer (OffsetDateTime doesn't have ZoneId)
- Changed ObjectMapper from Spring-injected to internally created in NotificationService and OutboxProcessor to avoid context loading issues in @SpringBootTest integration tests (Spring Boot 4 autoconfiguration scope)
- watchlist_entries table lacks user_id column; resolved recipient via JOIN on tenant_mandates (active mandates)

### Completion Notes List

- ✅ Replaced `spring-boot-starter-mail` with `com.resend:resend-java:4.13.0` — no code imported from `org.springframework.mail`
- ✅ Created `notification_outbox` table via Flyway migration V20260320_001 with indexes on (status, next_retry_at) and (tenant_id)
- ✅ Implemented transactional outbox pattern: PartnerStatusChangedListener creates outbox records within event transaction context
- ✅ OutboxProcessor polls every 60s, processes batch of 50, sends via ResendEmailSender with exponential backoff
- ✅ Alert storm protection: digest mode when tenant exceeds 10 alerts/day (configurable via risk-guard-tokens.json)
- ✅ Demo mode: logs email content at INFO level, marks SENT without calling Resend API
- ✅ Missing API key graceful degradation: WARN at startup, outbox records still created
- ✅ Health indicator reports lastRun, pendingCount, failedCount, lastEmailsSent (always UP)
- ✅ Localized email templates: HU and EN with masked tax number, SHA-256 hash, disclaimer
- ✅ Cross-module facade calls: getUserEmail() and getUserPreferredLanguage() on IdentityService
- ✅ PiiUtil.maskEmail() added for PII-safe logging
- ✅ All 388 backend tests pass (0 regressions), 399 frontend tests pass
- ✅ New tests: OutboxProcessorTest (7), ResendEmailSenderTest (4), EmailTemplateRendererTest (9), OutboxHealthIndicatorTest (3), PartnerStatusChangedListenerTest (+4 = 8 total)
- ✅ Resolved review finding [CRITICAL]: C1 — sha256Hash now populated from PartnerStatusChanged event (event enriched with hash at publish time)
- ✅ Resolved review finding [HIGH]: H1 — countTodayAlertsByTenant now filters by PENDING+SENT status per AC6
- ✅ Resolved review finding [HIGH]: H2 — handleRetry WARN log now includes tenantId and masked tax number per AC5
- ✅ Resolved review finding [HIGH]: H3 — Added 5 tests to NotificationServiceTest for createAlertNotification, digest mode, getOutboxStats
- ✅ Resolved review finding [HIGH]: H4 — Added 2 tests to ResendEmailSenderTest for successful send and ResendException paths using Mockito inline mocks
- ✅ All 395 backend tests pass (0 regressions) after review fixes
- ✅ Review round 2: Fixed missing i18n key `notification.watchlist.emailNotifications` in both hu/en
- ✅ Review round 2: Localized digest email table headers via MessageSource (was hardcoded English)
- ✅ Review round 2: Registered JavaTimeModule on standalone ObjectMappers in OutboxProcessor and NotificationService
- ✅ Review round 2: Fixed exponential backoff off-by-one (2^retryCount not 2^newRetryCount per AC5)
- ✅ Review round 2: Removed deprecated 5-arg PartnerStatusChanged.of() — all callers migrated to 6-arg
- ✅ Review round 2: Renamed misleading test + added handleRetry verification for null email path
- ✅ All 395 backend tests pass (0 regressions) after review round 2 fixes

### Change Log

- 2026-03-20: Story 3.8 implementation complete — Resend email alerts with transactional outbox pattern, exponential backoff, digest mode, demo mode, health indicator, localized templates
- 2026-03-20: Code review (AI) — 1 Critical, 4 High, 4 Medium, 2 Low issues found. Status reverted to in-progress. 4 action items added to Review Follow-ups section (C1: sha256Hash always null; H1: digest count misses SENT; H2: WARN log missing tenant+tax; H3: NotificationService untested; H4: ResendEmailSender send paths untested via WireMock).
- 2026-03-20: Addressed code review findings — 5 items resolved (1C/4H). C1: enriched PartnerStatusChanged event with sha256Hash field, populated at source (ScreeningService + WatchlistMonitor). H1: added PENDING+SENT status filter to countTodayAlertsByTenant. H2: added tenantId + masked tax number to WARN log. H3: added 5 tests for NotificationService outbox operations. H4: added 2 Mockito-based tests for ResendEmailSender send/failure paths. All 395 backend tests pass.
- 2026-03-20: Code review round 2 (AI) — 1 High, 4 Medium, 1 Low found and auto-fixed. H1: added missing i18n key `notification.watchlist.emailNotifications` (FE-1 incomplete). M1: renamed misleading test + added handleRetry assertion for null email path. M2: localized digest email table headers (was hardcoded English). M3: registered JavaTimeModule on standalone ObjectMappers for OffsetDateTime safety. M4: fixed exponential backoff off-by-one (first retry now 30s per AC5, was 60s). L2: removed unused @Deprecated 5-arg PartnerStatusChanged.of() factory method. All 395 backend tests pass (0 regressions).

### File List

**Created:**
- backend/src/main/resources/db/migration/V20260320_001__create_notification_outbox.sql
- backend/src/main/java/hu/riskguard/notification/domain/OutboxProcessor.java
- backend/src/main/java/hu/riskguard/notification/internal/ResendEmailSender.java
- backend/src/main/java/hu/riskguard/notification/internal/EmailTemplateRenderer.java
- backend/src/main/java/hu/riskguard/core/config/OutboxHealthState.java
- backend/src/main/java/hu/riskguard/core/config/OutboxHealthIndicator.java
- backend/src/test/java/hu/riskguard/notification/domain/OutboxProcessorTest.java
- backend/src/test/java/hu/riskguard/notification/internal/ResendEmailSenderTest.java
- backend/src/test/java/hu/riskguard/notification/internal/EmailTemplateRendererTest.java
- backend/src/test/java/hu/riskguard/core/config/OutboxHealthIndicatorTest.java

**Modified:**
- backend/build.gradle (replaced spring-boot-starter-mail with resend-java)
- backend/src/main/java/hu/riskguard/core/config/RiskGuardProperties.java (added Email + Outbox inner classes)
- backend/src/main/java/hu/riskguard/core/events/PartnerStatusChanged.java (added sha256Hash field — C1 review fix)
- backend/src/main/java/hu/riskguard/core/util/PiiUtil.java (added maskEmail)
- backend/src/main/java/hu/riskguard/notification/domain/NotificationService.java (added createAlertNotification, getOutboxStats)
- backend/src/main/java/hu/riskguard/notification/domain/PartnerStatusChangedListener.java (added outbox record creation, sha256Hash from event — C1 review fix)
- backend/src/main/java/hu/riskguard/notification/domain/OutboxProcessor.java (WARN log with tenant+masked tax — H2 review fix)
- backend/src/main/java/hu/riskguard/notification/internal/NotificationRepository.java (added outbox CRUD methods, PENDING+SENT filter — H1 review fix)
- backend/src/main/java/hu/riskguard/notification/internal/ResendEmailSender.java (added test constructors — H4 review fix)
- backend/src/main/java/hu/riskguard/screening/domain/ScreeningService.java (added getAuditHashByVerdictId facade — C1 review fix)
- backend/src/main/java/hu/riskguard/screening/domain/WatchlistMonitor.java (enriched event with sha256Hash — C1 review fix)
- backend/src/main/java/hu/riskguard/screening/internal/ScreeningRepository.java (added findAuditHashByVerdictId — C1 review fix)
- backend/src/main/java/hu/riskguard/identity/domain/IdentityService.java (added getUserEmail, getUserPreferredLanguage)
- backend/src/main/java/hu/riskguard/identity/internal/IdentityRepository.java (added findEmailById, findPreferredLanguageById)
- backend/src/main/resources/application.yml (added email + outbox config)
- backend/src/test/resources/application-test.yml (added email + outbox test config)
- backend/src/main/resources/application-staging.yml (added email + outbox config)
- backend/src/main/resources/application-prod.yml (added email + outbox config)
- backend/src/main/resources/messages_hu.properties (expanded email template keys)
- backend/src/main/resources/messages_en.properties (expanded email template keys)
- backend/src/test/java/hu/riskguard/notification/domain/PartnerStatusChangedListenerTest.java (added outbox tests, sha256Hash assertion — C1 review fix)
- backend/src/test/java/hu/riskguard/notification/domain/NotificationServiceTest.java (added outbox operation tests — H3 review fix)
- backend/src/test/java/hu/riskguard/notification/internal/ResendEmailSenderTest.java (added Mockito-based send/failure tests — H4 review fix)
- frontend/app/i18n/hu/notification.json (added email keys)
- frontend/app/i18n/en/notification.json (added email keys)
