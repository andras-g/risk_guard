---
type: architecture-impact-note
date: '2026-03-10'
author: 'Andras'
trigger: 'Government Data Source Validation Research (2026-03-10)'
related_research: '_bmad-output/planning-artifacts/research/technical-Government-Data-Source-Validation-research-2026-03-10.md'
affected_stories:
  - '2.2 — Parallel Scraper Engine and Outage Resilience'
  - '2.3 — VerdictEngine and SnapshotDataParser'
affected_adrs:
  - 'ADR-4 (Scraper Architecture)'
new_story_required: '2.2.1 — Real Data Source Adapter Replacement'
superseded_by: '_bmad-output/planning-artifacts/sprint-change-proposal-2026-03-12.md'
superseded_date: '2026-03-12'
---

> **SUPERSEDED (2026-03-12)**
>
> Key findings in this note have been **invalidated** by subsequent research (session ses_3226, 2026-03-11) and live verification (2026-03-12):
>
> - **Finding 2 ("Cégközlöny API is completely open")** — **INCORRECT.** All cegportal.im.gov.hu endpoints now require Cloudflare Turnstile tokens. The original curl test likely used a valid token from an active browser session without realizing it. Verified 2026-03-12: every endpoint returns `error.token.null` (HTTP 400).
> - **Story 2.2.1 scope** — **INVALID.** Both data sources (NAV reCAPTCHA + Cégközlöny Turnstile) require bot-protection bypass. Owner decision: no CAPTCHA bypass for government portals. Story 2.2.1 is DEFERRED.
> - **Replacement:** NAV M2M API (`nav-gov-hu/M2M`) and NAV Online Számla API (`nav-gov-hu/Online-Invoice`) are the legitimate data access paths. See the superseding document for full analysis.
>
> **Read instead:** `_bmad-output/planning-artifacts/sprint-change-proposal-2026-03-12.md`

# Architecture Impact Note: Government Data Source Validation Findings

**Date:** 2026-03-10
**Triggered by:** Hands-on validation of Hungarian government data sources
**Severity:** High — requires adapter replacement and story update

---

## 1. ADR-4 (Scraper Architecture) Amendment

### What Remains Valid

The **Hexagonal Ports and Adapters pattern** defined in ADR-4 is validated by the research findings. The architecture correctly anticipated that government data sources would need independent adapter implementations with isolated failure modes.

The key architectural decisions that remain sound:

- Port interface per data source (each source has its own adapter)
- Independent circuit breaker and timeout per adapter
- StructuredTaskScope parallel execution of independent sources
- Health tracking per source with independent outage detection

### What Changes

| ADR-4 Assumption | Reality | Amendment |
|------------------|---------|-----------|
| Adapters use JSoup for HTML scraping | Both confirmed sources return JSON | JSoup is no longer needed for any confirmed source |
| CSS selectors for data extraction | JSON field paths for data extraction | Jackson ObjectMapper replaces JSoup parsing |
| 3 separate HTTP calls to 3 sources | NAV returns 3 databases in 1 call; Cégközlöny is 1 call; e-Cégjegyzék unavailable | 2 parallel API calls (NAV + Cégközlöny) for MVP |
| All sources are scrapable HTML pages | NAV has reCAPTCHA; Cégközlöny is open JSON; e-Cégjegyzék requires contract | Mixed access patterns |

**Recommended ADR-4 amendment:** Replace "JSoup-based HTML scraping adapters" with "JSON API client adapters using HttpClient + Jackson. Playwright reserved for reCAPTCHA token generation (NAV only)."

---

## 2. Impact on Story 2.2 (Parallel Scraper Engine and Outage Resilience)

**Story status:** Done
**Impact severity:** High — adapters hit fictional endpoints

### What is Sound

The parallel engine infrastructure built in Story 2.2 is architecturally correct:

- `StructuredTaskScope` parallel fork pattern works for any adapter implementation
- Circuit breaker wiring per adapter is correct regardless of underlying HTTP mechanism
- Timeout handling and outage detection are adapter-agnostic
- Health dashboard reporting per source remains valid

### What Needs Replacement

The adapter implementations themselves target **fictional URLs with fictional CSS selectors**:

| Adapter | Problem | Fix |
|---------|---------|-----|
| `NavDebtAdapter` | Targets non-existent `nav.gov.hu/api/adosok/{taxNumber}` | Replace with `NavMultiQueryAdapter` calling `backend-www05.nav.gov.hu/api/multiLekerdezo` |
| `ECegjegyzekAdapter` | Targets a site that prohibits scraping (TOS violation) | Remove for MVP; future: `OccsECegjegyzekAdapter` (requires contract) |
| `CegkozlonyAdapter` | Uses JSoup on wrong URL | Replace with `CegkozlonyApiAdapter` calling `cegportal.im.gov.hu/api/cegkozlony/announcements/tax-number` |

**Conclusion:** A follow-up story is required to replace adapters while preserving the parallel execution infrastructure.

---

## 3. Impact on Story 2.3 (VerdictEngine and SnapshotDataParser)

**Story status:** In review
**Impact severity:** Medium — JSONB field paths need updating, but evaluation logic is correct

### What is Sound

The VerdictEngine's core logic remains correct:

- Priority-ordered evaluation (critical flags override informational signals)
- Freshness model (tiered staleness with verdict degradation)
- Multi-source aggregation pattern
- RELIABLE / AT_RISK / INCOMPLETE verdict taxonomy

### What Needs Updating

The `SnapshotDataParser` references fictional JSONB structures that do not match real API responses:

| Current Reference | Real Data | Logic Change |
|-------------------|-----------|--------------|
| `nav-debt.hasPublicDebt` (boolean) | `koztartozasmentes.totalElements > 0` = CLEAN | **Inverted logic**: presence on list = debt-free (positive signal) |
| — | `vegrehajtas.totalElements > 0` = AT_RISK | New field: enforcement proceedings (negative signal) |
| `cegkozlony.hasInsolvencyProceedings` (boolean) | `chapter_title` contains risk keywords | String matching vs. boolean: scan for "felszámolás", "csőd", "végelszámolás", "kényszertörlés" |

**Timing:** These parser changes should be made when real adapters are wired (Story 2.2.1), not before.

---

## 4. New Story Required: 2.2.1 — Real Data Source Adapter Replacement

### Scope

| Task | Description | Complexity | Blocker |
|------|-------------|------------|---------|
| `CegkozlonyApiAdapter` | HttpClient GET + Jackson JSON parsing of `cegportal.im.gov.hu` API | Low | None — can start immediately |
| `NavMultiQueryAdapter` | JSON API client for `backend-www05.nav.gov.hu/api/multiLekerdezo` | Medium | reCAPTCHA spike needed first |
| reCAPTCHA spike | Test Playwright headless browser for token generation | Medium | Playwright sidecar setup |
| `SnapshotDataParser` update | Map real JSON fields to verdict evaluation inputs | Low | After adapters produce real data |
| JSoup removal | Remove JSoup dependency from scraping module | Trivial | After all JSoup adapters deleted |
| Delete fictional adapters | Remove `NavDebtAdapter`, `ECegjegyzekAdapter`, `CegkozlonyAdapter` | Trivial | After replacements are verified |

### Suggested Task Order

1. **CegkozlonyApiAdapter** — zero blockers, proves the real adapter pattern works
2. **reCAPTCHA spike** — determines feasibility of NAV integration
3. **NavMultiQueryAdapter** — depends on spike results
4. **SnapshotDataParser update** — after both adapters produce real data
5. **Delete old adapters + JSoup** — cleanup after verification

### Acceptance Criteria (Draft)

- [ ] `CegkozlonyApiAdapter` calls real endpoint and returns parsed announcement data
- [ ] `NavMultiQueryAdapter` obtains reCAPTCHA token and calls real endpoint (or is documented as blocked with fallback strategy)
- [ ] `SnapshotDataParser` correctly maps real JSON fields to verdict inputs
- [ ] All fictional adapters and JSoup dependency removed
- [ ] Existing parallel execution and circuit breaker tests still pass
- [ ] Integration test with real tax number (25168879242) returns expected results

---

## 5. Source Availability and Verdict Quality Matrix

What verdict can RiskGuard produce depending on which sources are available?

| NAV Available | Cégközlöny Available | e-Cégjegyzék Available | Verdict Quality |
|:---:|:---:|:---:|---|
| Yes | Yes | No (MVP) | **Good** — debt-free status + insolvency detection. Gap: cannot detect dissolved companies. |
| Yes | No | No | **Partial** — debt-free status only. No insolvency detection. Verdict capped at INCOMPLETE for insolvency dimension. |
| No | Yes | No | **Partial** — insolvency detection only. No debt-free status. Verdict capped at INCOMPLETE for debt dimension. |
| No | No | No | **None** — all sources unavailable. Verdict = INCOMPLETE. |
| Yes | Yes | Yes (future) | **Full** — all dimensions covered. Can detect zombie companies (dissolved but appearing clean). |

---

## 6. Missing Data Without e-Cégjegyzék — Product Stakeholder Summary

This section is intended for product stakeholders to understand the data completeness situation.

### What RiskGuard CAN Tell Users Today (NAV + Cégközlöny)

- **"Is my partner tax-compliant?"** — YES, via NAV debt-free list and enforcement data
- **"Is my partner going bankrupt?"** — YES, via Cégközlöny insolvency announcements
- **"Is my partner's name and address consistent?"** — YES, cross-validated from both sources
- **"What is my partner's company registration number?"** — YES, from Cégközlöny

### What RiskGuard CANNOT Tell Users Without e-Cégjegyzék

- **"Is this company still legally active?"** — NO. A company can be "törölt" (struck off the registry) but still appear clean on NAV/Cégközlöny for a period.
- **"What type of company is this?"** — NO. Cannot distinguish Kft. vs. Zrt. vs. Bt. vs. Egyéni vállalkozó.
- **"How old is this company?"** — NO. Cannot detect recently formed shell companies.
- **"Who owns/manages this company?"** — NO. Director and ownership information requires e-Cégjegyzék.
- **"What is the registered capital?"** — NO. Financial capacity signal unavailable.

### Recommended Product Mitigation

When e-Cégjegyzék data is unavailable, the screening result should include a transparency notice:

> **Company registry status not verified.** The official company registry (e-Cégjegyzék) was not consulted. The screening result is based on tax authority and official gazette data only. To verify that this company is legally active and not dissolved, consult e-Cégjegyzék directly at www.e-cegjegyzek.hu.

This maintains user trust and makes the data completeness gap explicit rather than hidden.

---

_This architecture impact note should be reviewed alongside the full research findings document and incorporated into sprint planning for the next cycle._
