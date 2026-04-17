# ADR-0003: Epic 10 audit trail architecture — Centralized `AuditService` facade

- **Status:** Accepted
- **Date:** 2026-04-17
- **Deciders:** Andras (PO)
- **Related artefacts:**
  - `_bmad-output/planning-artifacts/research/technical-audit-trail-architecture-epic-10-research-2026-04-17.md` — full research, decision matrix, integration analysis, and source citations
  - `_bmad-output/implementation-artifacts/epic-9-retro-2026-04-17.md` — Epic 9 retrospective that mandated this decision (action T2)
  - `_bmad-output/planning-artifacts/epics.md` §Epic 10 — scope of Stories 10.1–10.9 and cross-cutting constraints T1–T6

## Context

Epic 10 transforms EPR filing from a manual template-quantity flow into a fully automated product-first pipeline. The Registry becomes the single source of truth; filings are computed from `(invoice lines × Registry components × multi-layer packaging ratios)` and submitted as OKIRkapu XML (ADR-0002). Every change that flows into a submitted XML — registry edits, AI classifications, bootstrap imports from 3 months of NAV invoices, aggregation runs, submissions — must be auditable against the Hungarian EPR regulatory regime.

Epic 10 is the first risk_guard epic where the audit trail *is* the product, not a supporting concern. Compliance Model C (per `epics.md:884`) makes the live Registry drive the filing calculation while every submitted XML is preserved read-only in `epr_exports` as the authoritative record — but the *why* behind every number on that XML lives in the Registry-side audit trail. A submitted filing without its matching audit row is indefensible to a regulator.

The Epic 9 retrospective (2026-04-17) surfaced the risk that an audit pattern introduced ad-hoc in each Epic 10 story would drift across nine stories. Retro action T2 therefore mandated a deliberate architectural decision — made *before* any Story 10.1 migration SQL lands — that binds Stories 10.2–10.9 consistently. This ADR is that decision.

Four candidate patterns were evaluated, plus Hibernate Envers as a baseline comparator. The candidates and their core mechanics:

| Pattern | Core mechanic |
|---|---|
| **Centralized Audit Builder** (status quo in Epic 9) | Domain services explicitly call an audit repository/service; same transaction as the mutation. |
| **Domain-Event Emitter** (`@TransactionalEventListener`) | Mutating services publish events; listener writes audit row; transaction phase configurable. |
| **Spring Modulith Event Publication Registry** (`@ApplicationModuleListener`) | Async + `REQUIRES_NEW` + transactional listener backed by a persistent outbox (`event_publication` table). |
| **Spring AOP `@Audited`** | Annotation on service methods; `@Aspect` advice writes the audit row. |
| **Hibernate Envers** *(baseline)* | Auto-generates mirror `_AUD` table per `@Audited @Entity`. |

Envers is immediately ruled out: `backend/build.gradle:70` reads *"All domain persistence uses jOOQ exclusively — no JPA repositories or @Entity annotations."* Adopting Envers would require reintroducing JPA across the whole Registry — a far larger refactor than Epic 10's scope.

The remaining four were scored against an 11-criterion weighted decision matrix (full matrix in the research doc §4); criteria derived from Epic 10's non-functional requirements (atomicity for compliance, SPoC enforceability for T2, provenance fidelity for Story 10.8, 3000-invoice batch throughput for Story 10.4, T4 tx-pool refactor compatibility, backward compatibility with Epic 9). The ranking is robust to ±2 perturbation of any single weight.

## Decision

**Adopt the Centralized Audit Builder pattern, implemented as a module-owned `AuditService` facade at `hu.riskguard.epr.audit.AuditService`, enforced as the sole write path to audit tables by ArchUnit.** Spring Modulith events (`@ApplicationModuleListener`) remain reserved for *inter-module signals* (as with the existing `PartnerStatusChanged` → `PartnerStatusChangedListener`); they do not carry audit writes.

The chosen pattern is the evolution of what already exists in the codebase (`RegistryAuditRepository` + `RegistryService.emitAudit()` helpers at `RegistryService.java:200-248`). Epic 10 promotes this pattern from an Epic-9-scoped repository into a module-owned facade, then makes that facade the sole permitted write path. This is refactor-shaped, not rewrite-shaped: ~200 LOC, one dev-day in Story 10.1 Task 0.

### Component shape

```
hu.riskguard.epr.audit
├── AuditService                        // @Service — the facade, the SPoC
├── events/                             // record DTOs carrying provenance
│   ├── FieldChangeEvent                // registry field-level changes
│   ├── AggregationContributionEvent    // Story 10.8: (runId, invoiceLineId, kfCode, weightKg, ProvenanceTag)
│   └── SubmissionAuditEvent            // Story 10.9: immutable submission records
├── ProvenanceTag                       // enum: REGISTRY_MATCH | VTSZ_FALLBACK | UNRESOLVED | UNSUPPORTED_UNIT
└── internal/
    ├── RegistryAuditRepository         // Epic 9's existing jOOQ repo, now package-private to audit module
    └── AggregationAuditRepository      // Story 10.8: new jOOQ repo for aggregation_audit table
```

`RegistryAuditRepository` moves from `hu.riskguard.epr.registry.internal` into `hu.riskguard.epr.audit.internal` during Story 10.1 Task 0; all external call sites migrate to `AuditService.record*(...)`.

### Rules (ArchUnit-enforced)

Two rules added to `hu.riskguard.architecture.EpicTenInvariantsTest` in the `allowEmptyShould(true)` style already used in `EpicNineInvariantsTest`:

1. **`only_audit_package_writes_to_audit_tables`** — no class outside `..epr.audit..` may depend on any class in `..epr.audit.internal..`. All audit writes route through `AuditService`.
2. **`audit_service_is_the_facade`** — the `AuditService` class resides in `..epr.audit..`, is annotated `@Service`, and MUST NOT be annotated `@Transactional` (see "Hard rule" below).

### Hard rule (ArchUnit-enforced)

**`AuditService` MUST NOT carry `@Transactional`.** It inherits the caller's transaction. Marking it `@Transactional` would re-couple with the T4-forbidden `@Transactional`-across-NAV-HTTP anti-pattern that Story 10.1's orchestrator refactor tears out. Enforced at build time by the `notBeAnnotatedWith(Transactional.class)` clause on `audit_service_is_the_facade` (see `EpicTenInvariantsTest`), and documented as a class-level Javadoc comment.

### Batch-write path for Story 10.4

`AuditService.recordAggregationContributionsBatch(List<AggregationContributionEvent>)` **will be added in Story 10.4** for the 3000-invoice bootstrap. It will use jOOQ's batched-connection pattern (not `DSLContext.batchInsert()`, which is slow at this scale per the jOOQ user-group evidence cited in the research doc §5). Single round-trip per sub-batch.

### Observability

Micrometer counter `audit.writes{source=MANUAL|AI_SUGGESTED_CONFIRMED|AI_SUGGESTED_EDITED|VTSZ_FALLBACK|NAV_BOOTSTRAP}` instrumented inside the `AuditService` facade — one instrumentation point covers every call site.

### Applied across Stories 10.1–10.9

| Story | Audit touchpoint |
|---|---|
| 10.1 | **Task 0:** introduce `AuditService`, migrate `RegistryService`, add ArchUnit rules, perf sanity-check. |
| 10.2 | None (UI-only picker work). |
| 10.3 | AI classification — `AuditService.recordRegistryFieldChange(...)` with `source=AI_SUGGESTED_CONFIRMED`. |
| 10.4 | Bootstrap — `AuditService.recordAggregationContributionsBatch(...)`. |
| 10.5 | Aggregation runs — one `AggregationRunStarted` + one `AggregationRunCompleted` row per run. |
| 10.6 | None (filing UI is read-only). |
| 10.7 | Reuses 10.4's bootstrap hook. |
| 10.8 | `aggregation_audit` table + per-invoice-line provenance-tagged rows via `AuditService.recordAggregationContribution(...)`. |
| 10.9 | `AuditService.recordSubmission(...)` on OKIRkapu XML submission. |

## Consequences

### Positive

- **Atomicity by construction.** Mutation and audit commit together in the caller's transaction. An EPR filing cannot commit to the database without its audit row also committing — a hard requirement of Epic 10's compliance model, not just a technical nicety.
- **ArchUnit enforces consistency across nine stories.** T2's "applied consistently to all new audit paths in Stories 10.2–10.9" becomes a build-time guarantee, not a code-review convention. "No class outside `..epr.audit..` may depend on audit repositories" is a crisp one-line rule; the alternative patterns (events, AOP) could not express equivalent enforcement.
- **Provenance is lossless.** Story 10.8's `BigDecimal weightContributionKg` and `ProvenanceTag` ride as explicit record fields. AOP's `JoinPoint.getArgs()` model cannot carry these cleanly; events can but surface them as convention rather than a typed facade.
- **Smallest refactor blast radius.** Epic 9's existing `RegistryAuditRepository.insertAuditRow` call sites in `RegistryService` migrate to `AuditService.record*` in a single Story 10.1 commit; zero behavioral change.
- **No new operational surface.** No new system tables (unlike the Modulith registry's `event_publication` + `event_publication_archive`), no async executor configuration, no outbox retry semantics to reason about during incidents.
- **Best batch throughput for Story 10.4.** One INSERT per audit row via jOOQ batched-connection vs. ~4× DB round-trips per audited change under the Modulith registry.
- **T4 tx-pool refactor compatible.** Because `AuditService` inherits the caller's transaction rather than defining one, it composes with the Story 10.1 orchestrator pattern that moves NAV HTTP calls outside any transaction.
- **Forward-compatible.** If Epic 11+ ever requires audit fan-out (compliance export, search indexing, external registry push), event publication can be added *inside* `AuditService.record*` methods without touching any call site. The facade earns optionality without committing to it.

### Negative / accepted costs

- **Service layer is not audit-agnostic.** Domain services pass audit-relevant state to `AuditService` explicitly — there is a visible audit call at each mutation site. Accepted trade-off: the alternative patterns that promise "clean" service layers (events, AOP) sacrifice enforceability, which T2 weights higher than service-layer cleanliness.
- **Two patterns coexist briefly.** During Story 10.1, Epic 9's direct-repository calls continue to compile alongside the new facade. Convergence happens in the same Story 10.1 commit that introduces `AuditService`; the window is measured in pull-request review time, not sprints.
- **Future audit fan-out requires a retrofit inside the facade.** If external consumers (Elastic, an audit-query microservice) appear post-Epic-10, `AuditService.record*` methods grow event-publication calls internally. Call sites are untouched, but the facade itself becomes more complex. Accepted because the near-term cost of pre-building that machinery (Modulith registry + schema + retry ops) outweighs the speculative benefit.
- **Facade does not prevent every form of drift.** A developer could still pass misshapen provenance records, or forget to provide provenance at all for a new audit field. ArchUnit guards the write boundary; test and code-review guard the semantics. Accepted — perfect is not the goal; "pattern consistent across nine stories" is.
- **Abstraction is partly speculative until Story 10.8 lands.** `AggregationContributionEvent` and `AggregationAuditRepository` are designed from Story 10.8's brief; the actual implementation may surface refinements. Mitigation: Story 10.1 ships only the `AuditService` shell and the registry-side methods; aggregation-audit methods land with Story 10.8 where their shape is concrete.

## Alternatives considered

- **Domain-Event Emitter with `@TransactionalEventListener(BEFORE_COMMIT)`.** Decision-matrix score 257 (vs. 316 for the winner). Cleanest separation of concerns — service layer is audit-agnostic, a listener in the audit module converts events to rows. **Rejected** because ArchUnit cannot assert "every mutator publishes a corresponding event type"; with nine stories authoring new audit paths, convention-drift is a real hazard, and T2 explicitly weights enforceability highly. Also introduces two event types per audit domain (one for registry fields, one for aggregation contributions, etc.), proliferating ceremony without functional benefit when the codebase has exactly one audit consumer today.

- **Spring Modulith Event Publication Registry (`@ApplicationModuleListener`).** Decision-matrix score 214. Attractive on paper because Spring Modulith is already in the codebase (for `PartnerStatusChanged` cross-module signalling). **Rejected** because `@ApplicationModuleListener` expands to `@Async @Transactional(REQUIRES_NEW) @TransactionalEventListener` — async and in a new transaction. Audit writes become eventually consistent with the emitting mutation, meaning a filing could commit without its audit row until the Event Publication Registry retry succeeds. For EPR compliance math this is a *compliance hazard*, not just a latency concern. Secondarily: adds two system tables (`event_publication`, `event_publication_archive`) and ~4× DB round-trips per audited change — an outbox price paid without receiving the outbox benefit in a single-process modulith with a single audit consumer. Reserved for its actual fit: inter-module signals where async delivery is desired.

- **Spring AOP `@Audited` + `@Aspect` advice.** Decision-matrix score 209. Declarative at the call site; zero boilerplate in mutators. **Rejected** because (a) AOP's `JoinPoint.getArgs()` cannot cleanly carry dynamic provenance like Story 10.8's `BigDecimal weightContributionKg` without forcing method signatures to mirror audit schema; (b) Spring AOP is proxy-based, so `this.foo()` self-invocation silently bypasses the advice — a correctness hazard on any service method that calls a sibling audited method; (c) net-new framework to the codebase (zero `@Aspect` in `src/main` today), so adopting it is a cost without precedent to lean on.

- **Hibernate Envers.** Requires JPA entities and a Hibernate-managed persistence context. **Hard-rejected** on stack grounds: `build.gradle:70` forbids JPA. Adopting Envers would require rewriting every jOOQ repository as a JPA repository — a scope-inflating refactor that would overshadow Epic 10 itself.

- **Status quo — direct `RegistryAuditRepository` calls from every new Epic 10 caller.** The "do nothing" option. **Rejected** because T2 explicitly demanded a decision that enforces consistency; Epic 9's pattern works but has no boundary, meaning any new caller (Story 10.5's aggregator, Story 10.9's submission flow) could drift into a slightly different shape. The ArchUnit-enforced facade closes the boundary without changing the writing style.

## Revisit triggers

Reopen this ADR if any of the following:

- **Audit fan-out becomes a real requirement.** If Epic 11+ introduces a second consumer of audit rows (compliance export, search index, external registry push, notification pipeline), evaluate adding event publication inside `AuditService.record*` methods (Hybrid A evolution). Call sites stay; the facade itself grows.
- **Audit write volume outgrows the transactional path.** If production audit-write latency p95 exceeds acceptable thresholds for filing commits, consider moving selected audit paths to the Modulith registry with explicit at-least-once semantics. Decision reverses only if async delivery becomes acceptable for that path — e.g., bulk bootstrap audits (Story 10.4) could plausibly relax to eventually-consistent if Story 10.4's completion gate asserts audit-presence before the UI shows "complete".
- **Compliance model changes.** If the Hungarian EPR regime (or a successor EU PPWR regime) permits compliance attestation without a full Registry-side audit trail (unlikely but possible), the atomicity constraint that drives this decision weakens and async patterns become viable.
- **A cross-tenant audit search UI is commissioned.** Ad-hoc cross-table audit queries at scale likely justify projecting audit rows into a read-optimised view or an Elastic index, at which point internal event publication pays for itself.
- **Audit retention policy forces partitioning or archival.** Currently out of scope; at ≥12 months of production data, revisit whether `registry_entry_audit_log` + `aggregation_audit` need PostgreSQL declarative partitioning by `(tenant_id, timestamp)` or a separate archive store. Pattern stays; physical schema evolves.
- **Audit-table SQL-level immutability is required.** If a compliance review demands that audit rows be physically un-updateable at the database level (not just at the application level), revoke `UPDATE`/`DELETE` grants on audit tables for the application role. Does not affect this ADR's pattern, but would add a Flyway migration and a role-separation story.
