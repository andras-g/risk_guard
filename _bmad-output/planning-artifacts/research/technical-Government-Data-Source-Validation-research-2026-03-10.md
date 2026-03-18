---
stepsCompleted: [1, 2, 3, 4, 5]
inputDocuments:
  - "_bmad-output/planning-artifacts/research/technical-Scraper-Tech-Audit-research-2026-03-04.md"
  - "_bmad-output/planning-artifacts/architecture.md"
  - "_bmad-output/implementation-artifacts/2-2-parallel-scraper-engine-and-outage-resilience.md"
workflowType: 'research'
lastStep: 5
research_type: 'technical'
research_topic: 'Government Data Source Validation: Real API Endpoints and Access Methods'
research_goals: 'Validate actual Hungarian government data source availability, document real API endpoints, assess reCAPTCHA and contractual barriers, determine MVP-viable data sources'
user_name: 'Andras'
date: '2026-03-10'
web_research_enabled: true
source_verification: true
superseded_by: '_bmad-output/planning-artifacts/sprint-change-proposal-2026-03-12.md'
superseded_date: '2026-03-12'
---

# Research Report: Government Data Source Validation

**Date:** 2026-03-10
**Author:** Andras
**Research Type:** technical

---

## Technical Research Scope Confirmation

**Research Topic:** Government Data Source Validation: Real API Endpoints and Access Methods
**Research Goals:** Validate actual Hungarian government data source availability, document real API endpoints, assess reCAPTCHA and contractual barriers, determine MVP-viable data sources

**Technical Research Scope:**

- **Endpoint Validation**: Hands-on testing of assumed URLs vs. real government API endpoints
- **Authentication Barriers**: Identification of reCAPTCHA, contract, and access control requirements
- **Response Format Analysis**: Actual JSON response structures from live endpoints
- **MVP Viability Assessment**: Which sources can be integrated today vs. require negotiation
- **Architecture Impact**: Required changes to existing adapter implementations

**Research Methodology:**

- Direct browser-based testing of government portals
- Network inspector analysis of actual HTTP requests
- Live API calls with real tax numbers to verify response structures
- Cross-referencing assumed endpoints against actual government site behavior

**Scope Confirmed:** 2026-03-10

---

## Executive Summary

All 3 assumed government data sources turned out to be fundamentally different from what was assumed during initial architecture and story planning. The assumed URLs and scraping approaches (JSoup/HTML parsing with CSS selectors) are fictional. The real endpoints are JSON APIs with varying access requirements.

**Key finding:** One source (Cégközlöny) is immediately usable as a clean REST API with zero authentication. One source (NAV) is a JSON API protected by reCAPTCHA v3. One source (e-Cégjegyzék) requires a formal business contract and cannot be used in MVP.

**Impact:** Story 2.2 adapters (NavDebtAdapter, ECegjegyzekAdapter, CegkozlonyAdapter) must be completely replaced. JSoup dependency can be removed. The parallel execution pattern (StructuredTaskScope) remains valid.

---

## Finding 1: NAV (Tax Authority) Multi-Query API

### Assumed vs. Reality

| Aspect | Assumed | Reality |
|--------|---------|---------|
| URL | `nav.gov.hu/api/adosok/{taxNumber}` | `backend-www05.nav.gov.hu/api/multiLekerdezo` |
| Format | Unknown/HTML | JSON |
| Auth | None assumed | Google reCAPTCHA v3 |
| Parsing | JSoup with CSS selectors | Jackson JSON |
| Databases | 1 (debt list) | 3 in single response |

### Real Endpoint

```
GET https://backend-www05.nav.gov.hu/api/multiLekerdezo?torzsszam={taxNumber}&nev={name}&evho={YYYYMM}&size=100&t={recaptchaToken}
```

This is a hidden JSON API behind the public "Több adatbázis együttes lekérdezése" (multi-database query) page at `nav.gov.hu/adatbazisok/multilekerdezo`.

### Parameters

| Parameter | Description | Example |
|-----------|-------------|---------|
| `torzsszam` | Tax number (adószám) | `25168879242` |
| `nev` | Company name (partial match) | `4flow` |
| `evho` | Year-month for query period | `202603` |
| `size` | Page size | `100` |
| `t` | reCAPTCHA v3 token (single-use, ~2 min expiry) | `03AFcWeA5...` |

### Response Structure

The API returns a **single JSON response** containing 3 sections:

- **`koztartozasmentes`** — Debt-free taxpayers list (köztartozásmentes adózói adatbázis)
- **`afaalany`** — VAT subjects (áfaalany adatbázis)
- **`vegrehajtas`** — Enforcement proceedings (végrehajtás alatt álló adózók)

### Example Response (tax number 25168879242 — 4flow management Kft.)

**koztartozasmentes section:**
- `totalElements: 1` — Company IS on the debt-free list
- `content[0].megnevezes`: "4flow management Kft." (company name)
- `content[0].szekhely`: "1062 Budapest, Andrássy út 121." (registered address)
- `content[0].felvetelNapja`: "2024-11-09" (date added to clean list)

**afaalany section:**
- `totalElements: 0` — No VAT subject flags (clean)

**vegrehajtas section:**
- `totalElements: 0` — No enforcement proceedings (clean)

### Critical Blocker: reCAPTCHA v3

The `t=` parameter is a **Google reCAPTCHA v3 token**. This is a single-use token that expires in approximately 2 minutes. It cannot be obtained programmatically with a plain `HttpClient` — it requires a browser context that executes the reCAPTCHA JavaScript challenge.

**reCAPTCHA solution options (ranked):**

| Option | Approach | Complexity | Reliability |
|--------|----------|------------|-------------|
| A | Playwright headless browser | Medium | High — renders page, solves reCAPTCHA, extracts token |
| B | Contact NAV for M2M API access | Low (if granted) | Highest — similar to NAV Online Számla API OAuth flow |
| C | Hybrid: browser for token, HttpClient for API call | Medium | High — browser only needed for token generation |

### Implication for Existing Code

The 3 separate JSoup/HTML adapters built in Story 2.2 are hitting **fictional URLs with fictional CSS selectors**:

- `NavDebtAdapter` — targets a URL that does not exist
- `ECegjegyzekAdapter` — targets a URL that does not exist
- `CegkozlonyAdapter` — targets a URL that does not exist

All three need **complete replacement**, not modification.

### Cost

Free, public data. No contract or registration required. Only technical barrier is reCAPTCHA.

---

## Finding 2: Cégközlöny (Official Gazette of Companies)

### Assumed vs. Reality

| Aspect | Assumed | Reality |
|--------|---------|---------|
| URL | JSoup scraping of `cegkozlony.gov.hu` | `cegportal.im.gov.hu/api/cegkozlony/announcements/tax-number` |
| Format | HTML | JSON (paginated) |
| Auth | Unknown | **NONE** — completely open |
| Parsing | JSoup with CSS selectors | Jackson JSON |

### Real Endpoint

```
GET https://cegportal.im.gov.hu/api/cegkozlony/announcements/tax-number?taxNumber={taxNumber}&page=0&size=10
```

This is a **completely open REST API**. No authentication, no reCAPTCHA, no session tokens, no API key. Just a plain HTTP GET returning paginated JSON.

### Response Fields

| Field | Description | Example |
|-------|-------------|---------|
| `announcement_id` | Unique announcement identifier | `12345` |
| `appearance_date` | Publication date | `2024-06-15` |
| `business_name` | Company name | `4flow management Kft.` |
| `company_registration_number` | Cégjegyzékszám | `01-09-XXXXXX` |
| `headquarter` | Registered address | `1062 Budapest, Andrássy út 121.` |
| `chapter_title` | **Critical risk signal** | `Változások közzététele` |
| `collector_code` | Chapter code | `CK_I` |
| `tax_number` | Tax number | `25168879242` |

### chapter_title Risk Classification

The `chapter_title` field is the **critical risk signal**. Classification:

| chapter_title | Code | Risk Level | Meaning |
|---------------|------|------------|---------|
| Változások közzététele | CK_I | SAFE | Routine company changes |
| Felszámolási eljárás | — | **CRITICAL** | Liquidation proceedings |
| Csődeljárás | — | **CRITICAL** | Bankruptcy proceedings |
| Végelszámolás | — | **HIGH** | Voluntary winding-up |
| Kényszer-törlési eljárás | — | **CRITICAL** | Forced dissolution |

### Example Response (tax number 25168879242)

- 18 announcements across 2 pages
- **ALL** are "Változások közzététele" (CK_I) = routine changes
- Result: **Clean company** — no insolvency proceedings

### Integration Assessment

**THIS SOURCE IS READY TO USE TODAY.**

- Simple `HttpClient` GET request
- Jackson JSON deserialization
- No JSoup needed
- No authentication needed
- No contract needed
- Can be implemented in hours

### Cost

Free, no contract needed, no rate limiting observed.

---

## Finding 3: e-Cégjegyzék (Company Registry)

### Assumed vs. Reality

| Aspect | Assumed | Reality |
|--------|---------|---------|
| URL | JSoup scraping of `www.e-cegjegyzek.hu` | Prohibited by TOS |
| Public API | None assumed | None available publicly |
| Contractual API | Not considered | `occsz.e-cegjegyzek.hu` (OCCSZ) |

### Public Site Restrictions

The public site `www.e-cegjegyzek.hu` **explicitly prohibits automated/script access** in its Terms of Service. Scraping is not a viable approach.

### Contractual API (OCCSZ)

The contractual API is hosted at `occsz.e-cegjegyzek.hu`:

- **OCCSZ** = Országos Cégnyilvántartási és Céginformációs Szolgálat (National Company Registry and Information Service, Ministry of Justice)
- Requires a formal **"céginformáció szolgáltatási szerződés"** (company information service agreement)
- Fee structure unknown — typically per-query pricing (estimated 100–500 HUF/query) or monthly flat fee
- Companies like Opten, Bisnode/Dun & Bradstreet, CompanyWall already have such contracts

### Status

**REQUIRES BUSINESS NEGOTIATION.** Cannot be used in MVP without signing a formal contract with the Ministry of Justice.

---

## Source Availability Summary

| Source | Real Endpoint | Auth | Cost | API Quality | MVP Status |
|--------|--------------|------|------|-------------|------------|
| NAV multi-query | `backend-www05.nav.gov.hu/api/multiLekerdezo` | reCAPTCHA v3 | Free | JSON, single call, 3 databases | Needs headless browser |
| Cégközlöny | `cegportal.im.gov.hu/api/cegkozlony/announcements/tax-number` | NONE | Free | Clean REST JSON, paginated | **READY TODAY** |
| e-Cégjegyzék (OCCSZ) | `occsz.e-cegjegyzek.hu` | Contract required | Unknown fees | Unknown | Needs business negotiation |

---

## What Data Comes From Which Source

### NAV Multi-Query Provides

| Data Point | JSON Path / Field | Risk Relevance |
|-----------|-------------------|----------------|
| Debt-free status | `koztartozasmentes.totalElements > 0` | Is the company on the clean list? YES/NO |
| Enforcement proceedings | `vegrehajtas.totalElements > 0` | Is the company under tax enforcement? YES/NO |
| VAT subject status | `afaalany` section | Is the company flagged as VAT subject? |
| Company name | `koztartozasmentes.content[].megnevezes` | Identity confirmation |
| Address | `koztartozasmentes.content[].szekhely` | Identity confirmation |
| Date added to clean list | `koztartozasmentes.content[].felvetelNapja` | Freshness signal |

**Logic note:** `koztartozasmentes.totalElements > 0` means the company IS debt-free (inverted from the assumed `hasPublicDebt` boolean). Being on the `koztartozasmentes` list is a **positive** signal.

### Cégközlöny Provides

| Data Point | JSON Path / Field | Risk Relevance |
|-----------|-------------------|----------------|
| Insolvency proceedings | `chapter_title` contains risk keywords | Felszámolás, csőd, végelszámolás, kényszertörlés |
| Company legal changes history | All announcements chronologically | Pattern detection |
| Company name | `business_name` | Identity confirmation |
| Company registration number | `company_registration_number` (cégjegyzékszám) | Official identifier |
| Address | `headquarter` | Identity confirmation |
| Publication dates | `appearance_date` | Timeline of events |

**Risk keyword scanning:** The adapter must scan `chapter_title` for these Hungarian terms:
- `felszámolás` → liquidation (CRITICAL)
- `csőd` → bankruptcy (CRITICAL)
- `végelszámolás` → voluntary winding-up (HIGH)
- `kényszertörlés` → forced dissolution (CRITICAL)

### e-Cégjegyzék Would Provide (NOT Available for MVP)

| Data Point | Risk Relevance |
|-----------|----------------|
| Official company legal status (active/dissolved/liquidation) | Definitive status |
| Legal form (Kft., Zrt., Bt., etc.) | Entity type classification |
| Registered capital | Financial capacity signal |
| Director/officer names | Beneficial ownership |
| Full company history | Change pattern detection |
| Official registration date | Age of company (shell company risk) |

---

## Impact on Product: What is VISIBLE vs. MISSING Without e-Cégjegyzék

### WITH NAV + Cégközlöny Only (MVP)

| Capability | Source | Signal |
|-----------|--------|--------|
| Is partner debt-free? | NAV `koztartozasmentes` | Positive signal: on clean list |
| Is partner under tax enforcement? | NAV `vegrehajtas` | Negative signal: under enforcement |
| Is partner in liquidation/bankruptcy? | Cégközlöny `chapter_title` | Negative signal: insolvency keywords |
| Company name and address | Both sources confirm | Cross-validation |
| Company registration number | Cégközlöny | Official identifier |

**Verdicts producible:**
- **RELIABLE** — Clean on both sources (debt-free + no insolvency proceedings)
- **AT_RISK** — Any red flag on either source (debt or insolvency)
- **INCOMPLETE** — One or both sources unavailable

**Cross-source value:** A company can be debt-free on NAV but simultaneously in liquidation on Cégközlöny. Only cross-source checking catches this contradiction. This is the core value proposition of RiskGuard.

### MISSING Without e-Cégjegyzék

| Cannot Determine | Risk Impact |
|-----------------|-------------|
| Is the company legally active or already dissolved/struck off? | High — "zombie company" risk |
| Official legal form (Kft., Zrt., Bt., etc.) | Low — informational only |
| Registered capital amount | Medium — financial capacity |
| Who are the directors/owners? | Medium — beneficial ownership |
| Official registration date | Medium — shell company detection |
| Was the company recently formed? | Medium — shell company risk |

**Key risk gap:** A company could be "törölt" (deleted from registry) but still appear clean on NAV if deletion is recent. This is the "zombie company" scenario.

### Verdict Quality Assessment

| Verdict | Quality Without e-Cégjegyzék |
|---------|------------------------------|
| RELIABLE | Still meaningful — debt-free + no insolvency = good signal |
| AT_RISK | Fully reliable — any red flag is a red flag |
| Gap | Cannot detect "zombie companies" (dissolved but not yet flagged elsewhere) |

**Recommended UX mitigation:** Display a note such as _"Company registry status not verified (requires e-Cégjegyzék access)"_ when that source is unavailable. This maintains transparency about data completeness.

---

## Architecture Impact

### 1. Story 2.2 Adapters Need Complete Replacement

The existing adapters from Story 2.2 target fictional endpoints with fictional CSS selectors. They cannot be patched — they must be replaced entirely.

| Action | Old Component | New Component |
|--------|--------------|---------------|
| Delete | `NavDebtAdapter` (fictional JSoup scraper) | — |
| Delete | `ECegjegyzekAdapter` (fictional JSoup scraper) | — |
| Delete | `CegkozlonyAdapter` (fictional JSoup scraper) | — |
| Create | — | `NavMultiQueryAdapter` (JSON API client, needs reCAPTCHA solution) |
| Create | — | `CegkozlonyApiAdapter` (simple HttpClient GET + Jackson JSON parsing) |
| Future | — | `OccsECegjegyzekAdapter` (when contract is signed) |

### 2. JSoup Dependency Removal

JSoup can be **removed** from the scraping module. Both confirmed real sources (NAV and Cégközlöny) return JSON responses. No HTML parsing is needed for any confirmed data source.

### 3. Parallel Execution Pattern Remains Valid

The `StructuredTaskScope` parallel pattern from Story 2.2 is **still valid**:
- Fork NAV call + Cégközlöny call in parallel
- Different government portals with independent failure modes
- Independent timeout and circuit breaker per source
- The pattern works regardless of whether the underlying call is JSoup HTML scraping or HttpClient JSON fetching

### 4. VerdictEngine and SnapshotDataParser Updates (Story 2.3)

The VerdictEngine and SnapshotDataParser currently reference fictional JSONB field paths:

| Current (Fictional) | Real | Notes |
|---------------------|------|-------|
| `nav-debt.hasPublicDebt` | `koztartozasmentes.totalElements > 0` = CLEAN | **Inverted logic!** Presence on list = clean |
| `cegkozlony.hasInsolvencyProceedings` | Scan `chapter_title` for risk keywords | String matching, not boolean |

**Inverted logic detail:** The current code assumes `hasPublicDebt = true` means AT_RISK. The real NAV data works inversely: `koztartozasmentes.totalElements > 0` means the company IS debt-free (CLEAN). The absence from the `koztartozasmentes` list is ambiguous — it could mean the company has debt OR simply hasn't applied for debt-free certification. The `vegrehajtas.totalElements > 0` is the direct negative signal (under enforcement = AT_RISK).

### 5. reCAPTCHA Token — Hardest Technical Challenge

The `t=` reCAPTCHA token for NAV is the most significant technical obstacle.

**Options ranked by recommendation:**

| Priority | Approach | Description | Timeline |
|----------|----------|-------------|----------|
| 1 (short-term) | Playwright headless browser | Already fits worker/ module architecture with sidecar Chrome. Navigate to NAV page, let reCAPTCHA JS execute, extract token, call API. | 1-2 days spike |
| 2 (medium-term) | NAV M2M API inquiry | NAV already provides the Online Számla API with proper OAuth for invoice reporting. Inquire if similar M2M access exists for the multi-query endpoint. | Weeks (bureaucracy) |
| 3 (long-term) | Official NAV API access | If NAV opens official programmatic access to the multi-query database. | Unknown timeline |

**Playwright approach detail:**
1. Launch headless Chromium via Playwright
2. Navigate to `nav.gov.hu/adatbazisok/multilekerdezo`
3. Fill in tax number and name fields
4. Wait for reCAPTCHA v3 to auto-solve (invisible challenge)
5. Intercept the `multiLekerdezo` API call from the network tab
6. Extract the `t=` token parameter
7. Optionally: reuse the token to make additional API calls within the ~2 minute window

---

## Recommended Action Items

| Priority | Action | Effort | Blocker |
|----------|--------|--------|---------|
| 1 — IMMEDIATE | Wire `CegkozlonyApiAdapter` with real endpoint | Hours | None — endpoint is open |
| 2 — THIS WEEK | Spike on NAV reCAPTCHA — test Playwright approach for token generation | 1-2 days | Need Playwright sidecar setup |
| 3 — THIS WEEK | Send inquiry to OCCSZ about e-Cégjegyzék API contract terms and pricing | Email | Business negotiation |
| 4 — STORY UPDATE | Create Story "2.2.1 — Real Data Source Adapter Replacement" | Story writing | Depends on spike results |
| 5 — BACKLOG | Update SnapshotDataParser for real JSON structures | 1 day | After adapters are replaced |
| 6 — BACKLOG | Remove JSoup dependency from scraping module | 30 min | After all JSoup adapters are deleted |

---

## Research Confidence Assessment

| Finding | Confidence | Basis |
|---------|-----------|-------|
| NAV endpoint URL and parameters | **HIGH** — verified via browser network inspector | Live testing |
| NAV response structure | **HIGH** — observed actual JSON response | Live API call with real tax number |
| NAV reCAPTCHA requirement | **CONFIRMED** — API returns 403 without valid token | Direct testing |
| Cégközlöny endpoint URL | **HIGH** — verified via browser network inspector | Live testing |
| Cégközlöny open access | **HIGH** — successful calls with no auth headers | Direct testing |
| Cégközlöny response structure | **HIGH** — observed actual JSON response | Live API call with real tax number |
| e-Cégjegyzék contract requirement | **MEDIUM-HIGH** — based on TOS review and industry knowledge | TOS analysis + known integrators |
| OCCSZ fee structure | **LOW** — estimated based on similar services | No direct verification |

---

## Appendix: Test Tax Numbers Used

| Tax Number | Company | Result |
|-----------|---------|--------|
| 25168879242 | 4flow management Kft. | Clean on NAV (debt-free since 2024-11-09), 18 routine announcements on Cégközlöny |

---

_End of research report._
