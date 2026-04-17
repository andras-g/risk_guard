# Code Review Findings — Epic 10 Story 10.1 Task 0 (Audit Architecture)

**Date:** 2026-04-17
**Reviewer:** Claude Code `bmad-code-review` (Blind Hunter + Edge Case Hunter + Acceptance Auditor, parallel layers)
**Spec:** `docs/architecture/adrs/ADR-0003-epic-10-audit-architecture.md`
**Retro action:** T2 (Epic 9 retrospective, 2026-04-17)
**Scope reviewed:** Uncommitted working tree — tracked + untracked, backend + architecture docs only.

## Files in scope

- `docs/architecture/adrs/ADR-0003-epic-10-audit-architecture.md` (new)
- `docs/architecture/adrs/INDEX.md` (+1 line)
- `_bmad-output/planning-artifacts/research/technical-audit-trail-architecture-epic-10-research-2026-04-17.md` (new)
- `backend/src/main/java/hu/riskguard/epr/audit/AuditService.java` (new)
- `backend/src/main/java/hu/riskguard/epr/audit/package-info.java` (new)
- `backend/src/main/java/hu/riskguard/epr/audit/internal/RegistryAuditRepository.java` (new — relocated)
- `backend/src/main/java/hu/riskguard/epr/registry/internal/RegistryAuditRepository.java` (deleted)
- `backend/src/main/java/hu/riskguard/epr/registry/domain/RegistryService.java` (migrated to `AuditService`)
- `backend/src/main/java/hu/riskguard/epr/registry/package-info.java` (ownership note updated)
- `backend/src/test/java/hu/riskguard/epr/registry/RegistryServiceTest.java` (mock target updated)
- `backend/src/test/java/hu/riskguard/architecture/EpicTenInvariantsTest.java` (new, 2 rules)

---

## Triage summary

- **27 findings** total
- **0 decision-needed** (all resolved below)
- **20 patch** — actionable, to be applied by the next session
- **3 defer** — already appended to `_bmad-output/implementation-artifacts/deferred-work.md` §"Deferred from: code review of 10-1-task-0-audit-architecture (2026-04-17)"
- **4 dismissed** — noise / false positive / accepted risk

## Decision log

| Decision | Question | Resolution |
|---|---|---|
| D1 | Perf sanity-check form (ADR line 79 requires it; no form specified) | **Deferred** — story-exit journal; revisit if Story 10.4 load test surfaces audit overhead. Logged as W3 in deferred-work.md. |
| D2 | Tenant-scope verification — facade-level or caller-level? | **Caller-level** — keep verification at caller, add Javadoc contract on facade read methods. → P17 |
| D3 | 9-positional-arg `recordRegistryFieldChange` refactor now or later? | **Now** — introduce `FieldChangeEvent` record matching ADR's eventual component shape. → P18 |
| D4 | `@NamedInterface("audit")` on package-info? | **Add it** — sibling `epr.*` and `hu.riskguard.*` modules use `@NamedInterface`. → P19 |
| D5 | Move `RegistryAuditEntry` + `AuditSource` into `hu.riskguard.epr.audit`? | **Move now** — eliminates `audit.internal` → `registry.domain` reverse import. → P20 |
| D6 | How to enforce caller-side `@Transactional` at build time? | **Accepted as code-review gate** — no build-time enforcement. ADR hard rule forbids `@Transactional(MANDATORY)` on the facade; an ArchUnit rule over every caller is high-complexity and high-noise. Dismissed. |

---

## Patches (20) — apply in this order

> Order rationale: schema/package moves (P19, P20) first so subsequent patches edit the moved types; then ArchUnit + facade hardening; then ADR/doc cleanup.

### P19 — Add `@NamedInterface("audit")` to audit package-info

**Source:** Edge Case Hunter (finding #8)
**File:** `backend/src/main/java/hu/riskguard/epr/audit/package-info.java`
**Why:** Sibling modules (`datasource.domain`, `notification.domain`, `core`, `jooq`) use Spring Modulith `@NamedInterface`. Without it, Modulith boundary verification treats `epr.audit` as an unnamed sub-module of `epr`, silently permitting any `epr.*` package to import `audit.internal` — defeating the facade's module isolation.
**Fix:** Add `@NamedInterface("audit")` annotation to the `package` declaration; import `org.springframework.modulith.NamedInterface`.
**Verification:** `./gradlew :backend:compileJava` passes; existing Modulith tests (if any) still green.

### P20 — Move `RegistryAuditEntry` + `AuditSource` into `hu.riskguard.epr.audit`

**Source:** Edge Case Hunter (finding #9)
**Files:**
- Move `backend/src/main/java/hu/riskguard/epr/registry/domain/RegistryAuditEntry.java` → `backend/src/main/java/hu/riskguard/epr/audit/RegistryAuditEntry.java`
- Move `backend/src/main/java/hu/riskguard/epr/registry/domain/AuditSource.java` → `backend/src/main/java/hu/riskguard/epr/audit/AuditSource.java`
- Update all imports in `AuditService.java`, `audit/internal/RegistryAuditRepository.java`, `RegistryService.java`, `RegistryServiceTest.java`, and any REST controllers that surface `RegistryAuditEntry`.

**Why:** These are audit-domain records but currently live under `registry.domain`, creating an `audit.internal` → `registry.domain` reverse import. `registry/package-info.java` already claims audit ownership moved out of registry — the types should follow.
**Verification:** `./gradlew :backend:compileJava` + `:backend:test --tests "hu.riskguard.epr.*"` passes; ArchUnit still green.

### P18 — Introduce `FieldChangeEvent` record; refactor `recordRegistryFieldChange`

**Source:** Blind Hunter (finding #14); resolves D3.
**Files:**
- New: `backend/src/main/java/hu/riskguard/epr/audit/events/FieldChangeEvent.java`
- Modify: `backend/src/main/java/hu/riskguard/epr/audit/AuditService.java`
- Modify: `backend/src/main/java/hu/riskguard/epr/registry/domain/RegistryService.java` — call sites
- Modify: `backend/src/test/java/hu/riskguard/epr/registry/RegistryServiceTest.java` — mock expectations

**Why:** Nine-positional-argument signature with three `UUID` + three `String` parameters is a correctness landmine — silent swap of `oldValue`/`newValue` or `productId`/`tenantId` at a call site is type-safe but semantically wrong. Also matches ADR-0003 §"Component shape" naming (`events/FieldChangeEvent`).

**Record shape** (from ADR line 46):
```java
package hu.riskguard.epr.audit.events;

import hu.riskguard.epr.audit.AuditSource;
import java.util.UUID;

public record FieldChangeEvent(
    UUID productId,
    UUID tenantId,
    String fieldChanged,
    String oldValue,
    String newValue,
    UUID changedByUserId,
    AuditSource source,
    String classificationStrategy,  // nullable — only populated when source indicates AI classification
    String modelVersion              // nullable — only populated when source indicates AI classification
) {}
```

**New public facade method:**
```java
public void recordRegistryFieldChange(FieldChangeEvent event) { ... }
```

**Migration path:** Replace the two existing overloads with the single record-based method. All callers in `RegistryService` update to pass `new FieldChangeEvent(...)`. No deprecated positional overload — single-commit cutover since `RegistryService` is the sole caller today.

**Verification:** `./gradlew :backend:test --tests "hu.riskguard.epr.*"` green; compile-time check that no old-signature call sites remain.

### P2 — Micrometer `audit.writes{source=...}` counter in `AuditService` (ADR BLOCKER)

**Source:** Acceptance Auditor (BLOCKER #1)
**File:** `backend/src/main/java/hu/riskguard/epr/audit/AuditService.java`
**Why:** ADR-0003 §"Observability" line 73 mandates: *"Micrometer counter `audit.writes{source=MANUAL|AI_SUGGESTED_CONFIRMED|AI_SUGGESTED_EDITED|VTSZ_FALLBACK|NAV_BOOTSTRAP}` instrumented inside the `AuditService` facade — one instrumentation point covers every call site."* Currently absent.

**Fix:** Inject `io.micrometer.core.instrument.MeterRegistry` in constructor; increment `Counter` tagged with `source` in each `record*` method. Example:

```java
private final MeterRegistry meterRegistry;
private final Map<AuditSource, Counter> counters;

public AuditService(RegistryAuditRepository registryAuditRepository, MeterRegistry meterRegistry) {
    this.registryAuditRepository = registryAuditRepository;
    this.meterRegistry = meterRegistry;
    this.counters = Arrays.stream(AuditSource.values())
        .collect(toMap(s -> s, s -> Counter.builder("audit.writes").tag("source", s.name()).register(meterRegistry)));
}

public void recordRegistryFieldChange(FieldChangeEvent event) {
    registryAuditRepository.insertAuditRow(...);
    counters.get(event.source()).increment();
}
```

**Verification:** Add a test in `AuditServiceTest` (see P13) that asserts the counter increments per call with the right tag — use `SimpleMeterRegistry`.

### P1 — ArchUnit: assert `AuditService` is NOT `@Transactional`

**Source:** Blind Hunter (finding #12) + Edge Case Hunter (finding #1)
**File:** `backend/src/test/java/hu/riskguard/architecture/EpicTenInvariantsTest.java`
**Why:** ADR-0003 §"Hard rule" line 65 forbids `@Transactional` on `AuditService`. The ADR claims this is "not ArchUnit-expressible" — it IS. Leaving it to code review is the exact drift T2 was meant to close.

**Fix:** Extend the `audit_service_is_the_facade` rule with `.andShould().notBeAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)`. Add Javadoc referencing ADR-0003 §"Hard rule". Also update ADR line 65 to remove the "not ArchUnit-expressible" claim.

**Verification:** `./gradlew :backend:test --tests "hu.riskguard.architecture.EpicTenInvariantsTest"` — rule fires when `@Transactional` is manually added to `AuditService`, passes otherwise.

### P5 — ArchUnit: switch `haveSimpleName` → FQN / package-based match for audit repositories

**Source:** Blind Hunter (finding #2) + Edge Case Hunter (finding #5)
**File:** `backend/src/test/java/hu/riskguard/architecture/EpicTenInvariantsTest.java` lines ~39-40
**Why:** `dependOnClassesThat().haveSimpleName("RegistryAuditRepository")` matches any class with that simple name anywhere — fragile to homonyms and silently defeated by renames. `EpicNineInvariantsTest` uses FQN (`only_registry_package_writes_to_product_packaging_components`) — follow the same precedent.

**Fix:** Replace with `.dependOnClassesThat().resideInAPackage("..epr.audit.internal..")`. This is both stricter (only audit-internal classes) and rename-proof.

**Verification:** Rule still fires if a non-audit class imports the relocated `RegistryAuditRepository`; passes under current arrangement.

### P6 — ArchUnit facade rule: use `..epr.audit..` (subpackage match)

**Source:** Edge Case Hunter (finding #6)
**File:** `EpicTenInvariantsTest.java` lines ~53-55
**Why:** `resideInAPackage("hu.riskguard.epr.audit")` is exact-package match only — a future refactor moving `AuditService` into a subpackage (e.g., `hu.riskguard.epr.audit.facade`) would break the rule despite satisfying the ADR invariant.
**Fix:** Change to `resideInAPackage("..epr.audit..")` or `resideInAPackage("hu.riskguard.epr.audit..")`.
**Verification:** Rule still passes under current arrangement.

### P7 — ArchUnit: tighten `BeanDefinitions`/`BeanFactoryRegistrations` exclusion

**Source:** Blind Hunter (finding #3)
**File:** `EpicTenInvariantsTest.java` lines ~44-45
**Why:** `haveSimpleNameNotContaining("BeanDefinitions")` matches substrings — production classes like `AuditBeanDefinitionsLoader` would silently bypass the rule.
**Fix:** Switch to `haveSimpleNameNotEndingWith("__BeanDefinitions")` and `haveSimpleNameNotEndingWith("__BeanFactoryRegistrations")`, aligned with Spring AOT code-gen convention.
**Verification:** ArchUnit rule still green.

### P15 — ArchUnit: narrow test-class exclusion

**Source:** Edge Case Hunter (finding #10)
**File:** `EpicTenInvariantsTest.java` lines ~35-36
**Why:** `haveSimpleNameNotEndingWith("Test")/("Tests")` lets fixture/seeder classes (`AuditSeeder`, `AuditTestSupport`, any `*IT`-suffixed integration test) bypass the rule.
**Fix:** Either add `ImportOption.DoNotIncludeTests` to the class importer AND drop the name-based exclusion, OR append `haveSimpleNameNotEndingWith("IT")`.
**Verification:** ArchUnit rule still green.

### P8 — Facade: clamp `page >= 0`, `size ∈ [1, 500]`

**Source:** Blind Hunter (finding #8) + Edge Case Hunter (finding #3)
**File:** `backend/src/main/java/hu/riskguard/epr/audit/AuditService.java` `listRegistryEntryAudit`
**Why:** Unvalidated `page < 0` or `size <= 0` produces a SQL error; huge `size` allows unbounded fetch. Pattern precedent: `RegistryBootstrapService.listCandidates` uses `Math.max(0, page)` + `Math.min(Math.max(1, size), 200)`.
**Fix:**
```java
public List<RegistryAuditEntry> listRegistryEntryAudit(UUID productId, int page, int size) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(Math.max(1, size), 500);
    return registryAuditRepository.listAuditByProduct(productId, safePage, safeSize);
}
```
**Verification:** Add unit tests for page=-1, size=0, size=10_000 cases.

### P9 — Facade: null-guards on identifiers and source

**Source:** Blind Hunter (finding #6) + Edge Case Hunter (finding #4)
**File:** `AuditService.java` `recordRegistryFieldChange(FieldChangeEvent)` (post-P18)
**Why:** A `null` `source` → NPE on `source.name()` inside the repository, rolling back the entire caller transaction (including the legitimate mutation). Facade should own these invariants.
**Fix:** Add `Objects.requireNonNull` on `event.productId()`, `event.tenantId()`, `event.source()`; `Assert.hasText` on `event.fieldChanged()`. Could also live in `FieldChangeEvent`'s compact constructor.
**Verification:** Unit test asserts `NullPointerException` is thrown from the facade with a clear message before any DB interaction.

### P17 — Javadoc contract on `AuditService` read methods: caller MUST pre-verify tenant

**Source:** Resolves D2
**File:** `AuditService.java` — `listRegistryEntryAudit`, `countRegistryEntryAudit`
**Why:** Facade is advertised as SPoC but read methods accept `productId`/`tenantId` without verifying product-tenant membership. Today the sole caller (`RegistryService.listAuditLog`) pre-verifies. User chose caller-level responsibility (D2=b) — make that contract explicit in Javadoc so future callers know.
**Fix:** Add class-level or method-level Javadoc:

```java
/**
 * Read methods DO NOT verify that the product belongs to the tenant. The caller
 * MUST invoke the registry's tenant-scoped lookup (e.g., {@link RegistryService#findProductByIdAndTenant})
 * BEFORE calling these methods. This is a deliberate design choice (see ADR-0003 code-review
 * of 2026-04-17, decision D2) to avoid double-work in the common path where the caller
 * has already loaded the product.
 */
```

**Verification:** Javadoc-only change; no test impact.

### P10 — Read path: guard `AuditSource.valueOf` on unknown values

**Source:** Blind Hunter (finding #7)
**File:** `backend/src/main/java/hu/riskguard/epr/audit/internal/RegistryAuditRepository.java` line ~69
**Why:** `AuditSource.valueOf(r.get(REGISTRY_ENTRY_AUDIT_LOG.SOURCE))` throws `IllegalArgumentException` if a legacy DB row contains an enum name that was later removed from the code — breaking every audit-list page that contains even one such row. Audit trails must be forward-compatible with their own history.
**Fix:**
```java
private static AuditSource parseSourceSafely(String raw) {
    try {
        return AuditSource.valueOf(raw);
    } catch (IllegalArgumentException e) {
        log.warn("Unknown AuditSource '{}' encountered in audit log; mapping to UNKNOWN", raw);
        return AuditSource.UNKNOWN;  // NEW enum constant — add to AuditSource
    }
}
```
**Also:** Add `UNKNOWN` to the `AuditSource` enum (now at `hu.riskguard.epr.audit.AuditSource` post-P20).
**Verification:** Unit test inserts a row with a synthetic enum name via raw jOOQ, asserts `parseSourceSafely` returns `UNKNOWN`.

### P11 — Read path: deterministic pagination tie-break

**Source:** Blind Hunter (finding #10)
**File:** `audit/internal/RegistryAuditRepository.java` — `listAuditByProduct`
**Why:** Two audit rows inserted in the same transaction can share `now()` timestamp (µs tie). `orderBy(TIMESTAMP.desc())` is then non-deterministic — cross-page duplication/omission possible, a regulator-facing defect.
**Fix:** `.orderBy(REGISTRY_ENTRY_AUDIT_LOG.TIMESTAMP.desc(), REGISTRY_ENTRY_AUDIT_LOG.ID.desc())`.
**Verification:** Integration test inserts two audit rows in one transaction with identical timestamp (force via `Clock`), asserts stable ordering across paginated fetches.

### P12 — Replace `verifyNoInteractions(auditService)` with negative-verify on specific method

**Source:** Blind Hunter (finding #11)
**File:** `backend/src/test/java/hu/riskguard/epr/registry/RegistryServiceTest.java` lines ~233, 260
**Why:** `verifyNoInteractions` breaks the moment any non-audit method lands on `AuditService` (ADR mentions future Micrometer bookkeeping, batch methods, etc.). Test should assert behavior, not total silence.
**Fix:** Replace each `verifyNoInteractions(auditService)` with `verify(auditService, never()).recordRegistryFieldChange(any())` (post-P18) or the specific method being tested.
**Verification:** Tests still pass; failure mode is now specific ("no field change recorded") rather than blanket.

### P13 — Add `AuditServiceTest` for facade delegation + Micrometer counter

**Source:** Blind Hunter (finding #13) + Edge Case Hunter (finding #7)
**File:** New — `backend/src/test/java/hu/riskguard/epr/audit/AuditServiceTest.java`
**Why:** The facade is now load-bearing for every Epic 10 audit path but has zero dedicated coverage. Tests must verify: (a) `recordRegistryFieldChange(event)` passes every field to the repository with matching values; (b) the Micrometer counter (P2) increments with the correct `source` tag; (c) null `event` rejected (P9); (d) page/size clamping in read methods (P8).

**Fix:** Create the test file. Use Mockito for repository, `SimpleMeterRegistry` for counter assertions. 5–8 tests:
1. `recordRegistryFieldChange_passesAllFieldsToRepository`
2. `recordRegistryFieldChange_incrementsCounterForSource` (parameterized over `AuditSource` values)
3. `recordRegistryFieldChange_rejectsNullEvent`
4. `recordRegistryFieldChange_rejectsNullSource`
5. `listRegistryEntryAudit_clampsNegativePageToZero`
6. `listRegistryEntryAudit_clampsOversizedToMax`
7. `listRegistryEntryAudit_clampsNonPositiveSizeToOne`

**Verification:** All new tests green; coverage report shows `AuditService` at >90%.

### P4 — Fix stale `@EventListener` Javadoc in `PartnerStatusChangedListener`

**Source:** Acceptance Auditor (gap)
**File:** `backend/src/main/java/hu/riskguard/notification/domain/PartnerStatusChangedListener.java` lines 24-26
**Why:** Javadoc says "Uses `@EventListener` (not `@TransactionalEventListener`)…" but the actual annotation at line 8/34-35 is `@ApplicationModuleListener`. ADR-0003 §"Revisit triggers" explicitly logged this as a follow-up that "should not get lost". Fixing it here closes the loop.
**Fix:** Update the Javadoc to describe `@ApplicationModuleListener` semantics (async + `REQUIRES_NEW` + transactional listener).
**Verification:** Javadoc-only; no test impact.

### P14 — Remove dangling "revisit trigger" bullet from ADR-0003

**Source:** Blind Hunter (finding #16)
**File:** `docs/architecture/adrs/ADR-0003-epic-10-audit-architecture.md` line ~132
**Why:** The bullet about `PartnerStatusChangedListener.java:24-26` becomes obsolete once P4 lands. An ADR's revisit-triggers section should be a policy document, not a follow-up scratchpad.
**Fix:** Delete the last bullet ("The stale `PartnerStatusChangedListener.java:24-26` Javadoc comment...").
**Verification:** ADR still renders correctly; no other reference to that bullet.

### P3 — ADR line 69 tense mismatch: "exists" → "will be added in Story 10.4"

**Source:** Acceptance Auditor (gap — ADR internal inconsistency)
**File:** `docs/architecture/adrs/ADR-0003-epic-10-audit-architecture.md` line ~69
**Why:** Line 69 reads: *"`AuditService.recordAggregationContributionsBatch(List<AggregationContributionEvent>)` exists for the 3000-invoice bootstrap."* But lines 79 (table) and 108 (accepted cost) defer it to Stories 10.4/10.8. The method is NOT in this diff. Code-doc drift at the moment of decision lock-in.
**Fix:** Change line 69 to: *"`AuditService.recordAggregationContributionsBatch(List<AggregationContributionEvent>)` **will be added in Story 10.4** for the 3000-invoice bootstrap..."*
**Verification:** Search ADR for `recordAggregationContributionsBatch` — single matching paragraph, consistent tense with the rest of the document.

### P16 — Drop backticks in ADR-0003 INDEX row for style consistency

**Source:** Edge Case Hunter (finding #12)
**File:** `docs/architecture/adrs/INDEX.md` — ADR-0003 row
**Why:** ADR-0001 and ADR-0002 titles in the index are bare (no backticks); ADR-0003 uses `` `AuditService` ``. Minor style drift.
**Fix:** Change the INDEX title cell to match sibling style — remove the code-tick formatting. Keep backticks inside the ADR body.
**Verification:** Visual diff on `INDEX.md`.

---

## Deferred (3) — already in deferred-work.md

See `_bmad-output/implementation-artifacts/deferred-work.md` §"Deferred from: code review of 10-1-task-0-audit-architecture (2026-04-17)":
- **W1:** Audit row timestamp relies on DB `DEFAULT now()` — pre-existing Epic 9 pattern.
- **W2:** `AuditSource.valueOf` in `RegistryService:~296` pre-existing caller-side parsing.
- **W3:** Perf sanity-check deferred to story-exit journal.

## Dismissed (4)

- **Duplicate `AuditService` class** (Blind #4) — Spring context-init enforces uniqueness at runtime.
- **ADR line-anchored references** (Blind #15) — cosmetic; ADR just authored.
- **Shim class at old path** (Edge #11) — theoretical drift; P5 hardens the boundary.
- **D6: build-time caller-`@Transactional` enforcement** — accepted as code-review gate; ADR forbids `@Transactional` on facade including `MANDATORY`, and an ArchUnit rule over every caller site is high-complexity + high-noise.

---

## Notes for the next session

1. **Before applying patches:** verify uncommitted state matches this review's scope. Run `git status` — expect the same `M/D/??` set listed under "Files in scope". If stale, re-run the review.
2. **Test strategy:**
   - Targeted tests first: `./gradlew :backend:test --tests "hu.riskguard.epr.*"` (~90s)
   - ArchUnit: `./gradlew :backend:test --tests "hu.riskguard.architecture.*"` (~30s)
   - Full suite ONCE at end. Never pipe `gradlew`.
3. **Order of execution:** P19 → P20 → P18 (schema/package moves first), then the rest in any order, finishing with ADR + INDEX doc patches (P3, P14, P16, and the ADR-line-65 update bundled with P1).
4. **Each patch has its own verification step.** Do not batch-verify at the end — catch regressions per-patch.
5. **P20 is the largest blast radius** — it touches every import of `RegistryAuditEntry` and `AuditSource`. Use IDE refactor support or `Grep` + `Edit` with care.
6. **P1 also requires editing the ADR:** strike the "not ArchUnit-expressible" phrasing on line 65 once the rule is in place.
7. **After all patches land:**
   - Full suite: `./gradlew :backend:test` (one pass)
   - Frontend sanity: `cd frontend && npm test -- --run` (~6s)
   - E2E if UI touched: `cd e2e && npm test` (N/A — this task is backend-only)
8. **Status tracking:** There is no Story 10.1 file yet (Epic 10 stories will be drafted via `bmad-create-story` before pickup). No `sprint-status.yaml` entry exists for `10-1-*`. When the story file is created, port this review's outcome into its `### Review Findings` section.

## Reviewer layer outputs (abridged)

- **Blind Hunter:** 16 findings (diff-only review, no project context).
- **Edge Case Hunter:** 14 findings (branch/boundary walk with project read access).
- **Acceptance Auditor:** 4 findings (2 BLOCKERS: Micrometer counter missing, perf sanity-check absent; 2 GAPS: batch method tense mismatch, stale Javadoc).

Full reviewer outputs are available in the session transcript from 2026-04-17; this document synthesizes them into the 20 actionable patches above.
