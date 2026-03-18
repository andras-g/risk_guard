# Sprint Change Proposal — 2026-03-12

## Trigger

Session `ses_3226` (2026-03-11) reverse-engineered the cegportal.im.gov.hu Angular frontend and discovered that the Cégközlöny API requires Cloudflare Turnstile tokens. Live verification on 2026-03-12 confirmed **all Cégközlöny endpoints are now blocked** (`error.token.null`). Combined with the owner decision to not bypass any government CAPTCHA/bot-protection, the entire scraping-based data acquisition strategy is invalidated.

## Key Findings

### Data Source Status (Verified 2026-03-12)

| Source | Protection | Automated Access | Legal Clean Path |
|--------|-----------|-----------------|------------------|
| **NAV multi-query** (nav.gov.hu) | reCAPTCHA v3 | ❌ Blocked (owner decision: no bypass) | NAV M2M API (representation-bound) |
| **Cégközlöny** (cegportal.im.gov.hu) | Cloudflare Turnstile | ❌ Blocked (was never open — original research was incorrect) | Contact IM / Ministry of Justice |
| **e-Cégjegyzék** (occsz.e-cegjegyzek.hu) | Login + contract | ❌ No credentials | OCCSZ contract (test system confirmed live at `occsztest.e-cegjegyzek.hu`) |

### NAV M2M API Discovery

NAV has an official M2M REST API (`nav-gov-hu/M2M` on GitHub) with:
- **Production:** `https://m2m.nav.gov.hu/rest-api/1.0/`
- **Test:** `https://m2m-dev.nav.gov.hu/rest-api/1.0/`
- **Auth:** Custom model — API Key from Ügyfélportál → phantom token → signing key → bearer token + signature
- **Relevant endpoints:**
  - `KoztartozasEgyenleg/{adoalanyAzonosito}` — public debt balance
  - `HianyzoBevallas/{adoalanyAzonosito}` — missing tax filings
  - `OsszesitettAdoszamla/{adoalanyAzonosito}` — summarized tax account
  - `TetelesAdoszamla/{adoalanyAzonosito}` — itemized tax account

**Critical constraint:** The M2M Adózó API is almost certainly representation-bound — you can only query companies you legally represent (as a törvényes képviselő or via EGYKE meghatalmazás). This means an accountant with representation for many companies can use it, but an arbitrary user cannot screen arbitrary partners.

### Corrected Research Finding

The 2026-03-10 research stated Cégközlöny API is *"completely open — no authentication, no reCAPTCHA, no session tokens."* This was **incorrect** — the researcher likely had a valid Turnstile token from a browser session without realizing it. As of 2026-03-12, every Cégközlöny endpoint requires `x-turnstile-token`.

---

## Change Proposal #1: Pivot to Demo-First MVP with Mock Data

### Strategic Decision

**Build the full product with realistic mock/demo data.** The complete UI, VerdictEngine, screening flow, watchlist, and monitoring will work end-to-end. Real data sourcing will be solved once the product concept is proven.

### Rationale

1. Manual data entry for such a small task is unacceptable — either fully automated or useless
2. All legitimate automated data access paths require business relationships that take 1-2 months to establish
3. An accountant (könyvelő) with EGYKE representation for many companies is the ideal first customer AND the data access channel
4. The accountant needs to see a working, compelling product before committing — that requires a demo
5. NAV M2M registration can be done in 1-2 months via an accountant's company
6. Once registered, the M2M Adózó API provides exactly the tax data needed

### Go-to-Market Path

```
NOW: Build full product with mock data → compelling demo
  └── MONTH 1-2: Demo to accountant → they see value
      └── Register at NAV M2M via accountant's company
          └── Wire real NAV M2M adapter (replacing mock adapter)
              └── Accountant's clients get real automated screening
                  └── Prove concept → expand to more data sources
```

### Impact on Current Sprint

| Item | Action |
|------|--------|
| **Story 2.3 (VerdictEngine — in-progress)** | **CONTINUE AS-IS** — pure logic, adapter-agnostic |
| **Story 2.3.5 (Verdict events — ready-for-dev)** | **CONTINUE AS-IS** — operates on VerdictStatus enums |
| **Story 2.2.1 (Real adapters — backlog)** | **DEFER AND REDESIGN** — will become "NAV M2M Adapter" story when API access is available |
| **Architecture doc** | **UPDATE** — remove Playwright worker, scraper sidecar, reCAPTCHA spike. Add NAV M2M integration notes. Mark data source layer as "mock for MVP, M2M for production" |
| **Story 2.2 (completed — fictional adapters)** | **KEEP** — the adapter pattern and JSONB storage model are correct. Mock adapters now serve as the demo data layer. Rename "fictional" to "demo" adapters. |

### What Gets Removed from MVP Scope

- ~~Playwright worker module~~
- ~~reCAPTCHA bypass spike~~
- ~~CegkozlonyApiAdapter (direct HTTP)~~
- ~~NavMultiQueryAdapter (reCAPTCHA-based)~~
- ~~Scraper health dashboard (Story 6.1) — no scrapers to monitor~~
- ~~Kill-switch (Story 6.2) — no scrapers to kill~~

### What Gets Added / Changed

| New Item | Description |
|----------|-------------|
| **Demo data seeder** | Seed realistic Hungarian company data into the screening system. Multiple scenarios: debt-free, has arrears, missing filings, insolvency proceedings. |
| **NAV M2M auth module (Phase 2)** | API Key activation flow, phantom token exchange, signing key management, bearer token lifecycle. |
| **NavM2mAdapter (Phase 2)** | Replaces mock adapter. Calls `KoztartozasEgyenleg`, `HianyzoBevallas` via M2M REST API. |
| **Updated VerdictEngine signals** | When M2M adapter is wired: parse `osszesJogcimTeljesFennalloHatralek` for debt, `hianyzoBevallas[]` for missing filings risk signal (new, not in original PRD). |

### Documents Requiring Update

| Document | What Changes |
|----------|-------------|
| **PRD** | Data source strategy section — acknowledge demo-first approach, M2M as production path |
| **Architecture** | Remove scraper/Playwright modules, add M2M integration layer, update data flow diagrams |
| **Epics** | Epic 2: remove scraper stories, add M2M stories (deferred). Epic 6: remove scraper monitoring stories |
| **Sprint status** | Story 2.2.1 status → deferred. Remove reCAPTCHA spike from backlog. |
| **Research** | Correct the 2026-03-10 finding. Add this proposal as the authoritative data source assessment. |

---

## Change Proposal #2: Reframe Story 2.2 Adapters as "Demo Mode"

### What

The existing fictional adapters from Story 2.2 (`HungarianCompanyRegistryAdapter`, `NavTaxAuthorityAdapter`) already generate realistic-looking data. Instead of treating them as throwaway placeholders, **rebrand them as the "Demo Mode" data layer**.

### Why

- They already exist and work
- The adapter interface pattern is correct
- When NAV M2M is available, we add `NavM2mAdapter` alongside (not replacing) the demo adapters
- Demo mode remains useful for sales demos, testing, and development even after production data is available

### Implementation

- Rename the adapter implementations: `Demo_` prefix or move to a `demo` package
- Add a configuration flag: `riskguard.data-source.mode=demo|live`
- When `live`: use NavM2mAdapter (requires M2M credentials)
- When `demo`: use existing fictional adapters with enriched seed data
- VerdictEngine doesn't care — it receives `SnapshotData` either way

### Effort

Minimal — rename + config flag. Half a day.

---

## Open Actions

| # | Action | Owner | Timeline |
|---|--------|-------|----------|
| 1 | Register at NAV Ügyfélportál for M2M test environment | Andras | 1-2 months (via accountant) |
| 2 | Update architecture doc to remove scraper modules | Dev | This sprint |
| 3 | Update epics to reflect demo-first strategy | SM | This sprint |
| 4 | Enrich demo data with realistic Hungarian company scenarios | Dev | This sprint |
| 5 | Post question in nav-gov-hu/M2M Discussions: "Can Adózó API query third-party tax numbers?" | Andras | This week |
| 6 | Initiate OCCSZ e-Cégjegyzék contract inquiry | Andras | When ready |

---

## Summary

The core product architecture (adapter pattern, VerdictEngine, JSONB storage) is **validated and correct**. The change is purely in the data acquisition layer: scraping → official M2M API. The business strategy shifts to demo-first, with an accountant as the ideal first customer who provides both market validation and legitimate data access.

---

## Change Proposal #3: NAV Online Számla Integration for EPR Module

### Context

Session ses_3226 extensively researched the EPR (Extended Producer Responsibility) module and the NAV Online Számla API. The key insight: **NAV Online Számla already contains every invoice** a Hungarian company issues or receives. This data is the foundation for both the EPR compliance module AND provides `QueryTaxpayer` for basic partner screening.

### NAV Online Számla API Technical Requirements

| Aspect | Detail |
|--------|--------|
| **Production URL** | `https://api.onlineszamla.nav.gov.hu/invoiceService/v3/{operation}` |
| **Test URL** | `https://api-test.onlineszamla.nav.gov.hu/invoiceService/v3/{operation}` (verified live 2026-03-12) |
| **Protocol** | XML over HTTPS (not JSON) |
| **API Version** | v3.0 (latest spec: 2026.02.12) |
| **Spec PDF** | `nav-gov-hu/Online-Invoice` repo → `docs/API docs/en/` (7.4MB English PDF) |
| **XSD Schemas** | 5 files: `invoiceApi.xsd`, `invoiceData.xsd`, `invoiceBase.xsd`, `invoiceAnnulment.xsd`, `serviceMetrics.xsd` |
| **Sample XMLs** | `sample/API sample/` — tokenExchange, queryTaxpayer, queryInvoiceDigest, queryInvoiceData |

### Authentication Model

**No OAuth.** User creates a "technikai felhasználó" (technical user) in the NAV Online Számla portal and provides 4 credentials to the app:

| Credential | How Stored | Used For |
|-----------|-----------|----------|
| **login** (technical user name) | Encrypted in DB | All API requests |
| **password** | SHA-512 hashed → sent as `passwordHash` | All API requests |
| **signing key** (cserekulcs) | Encrypted in DB, never transmitted | Computing SHA3-512 `requestSignature` per request |
| **tax number** (adószám) | Plain text | Identifies the company |

### Request Signing Algorithm

Every API request requires:
1. **`requestId`** — unique per request (e.g., UUID)
2. **`timestamp`** — ISO 8601 UTC
3. **`passwordHash`** — `SHA-512(password)`
4. **`requestSignature`** — `SHA3-512(requestId + timestamp + signingKey + <operation-specific-data>)`

The `software` block is also mandatory — identifies RiskGuard as the calling application:
```xml
<software>
  <softwareId>RISKGUARD-XXXXXXXXX</softwareId>
  <softwareName>RiskGuard</softwareName>
  <softwareOperation>ONLINE_SERVICE</softwareOperation>
  <softwareMainVersion>1.0.0</softwareMainVersion>
  <softwareDevName>RiskGuard Kft.</softwareDevName>
  <softwareDevContact>dev@riskguard.hu</softwareDevContact>
  <softwareDevCountryCode>HU</softwareDevCountryCode>
  <softwareDevTaxNumber>XXXXXXXX</softwareDevTaxNumber>
</software>
```

### API Operations Needed

| Operation | Endpoint | Use Case | Priority |
|-----------|----------|----------|----------|
| **`TokenExchange`** | POST `/tokenExchange` | Get exchange token (required before ManageInvoice, not for queries) | P2 |
| **`QueryTaxpayer`** | POST `/queryTaxpayer` | Verify partner tax number → name, address, incorporation type | **P0 — Screening** |
| **`QueryInvoiceDigest`** | POST `/queryInvoiceDigest` | List invoices by date range (OUTBOUND for EPR, INBOUND for partner info) | **P1 — EPR** |
| **`QueryInvoiceData`** | POST `/queryInvoiceData` | Get full invoice with line items (product descriptions, quantities, VTSZ codes) | **P1 — EPR** |
| `QueryInvoiceChainDigest` | POST `/queryInvoiceChainDigest` | Track invoice modifications/corrections | P3 |
| `QueryInvoiceCheck` | POST `/queryInvoiceCheck` | Check if specific invoice exists | P3 |

### What Invoice Line Items Contain (from XSD)

```
lineNumber           → Sequential number
lineDescription      → Product name (e.g., "Csavar M6x30 (100db)")
quantity             → Decimal (e.g., 5.0)
unitOfMeasure        → PIECE / KGM / MTR / LTR / etc.
unitOfMeasureOwn     → Custom unit text (e.g., "doboz")
unitPrice            → Unit price
lineNetAmount        → Net amount in invoice currency
lineNetAmountHUF     → Net amount in HUF
productCodes[]       → Array of:
  productCodeCategory → VTSZ / SZJ / KN / OWN
  productCodeValue    → e.g., "73181500" (customs tariff number)
  productCodeOwnValue → e.g., "SCR-M6-30-100" (own SKU code)
```

### EPR Data Flow

```
NAV Online Számla ──QueryInvoiceDigest──→ Invoice summaries (paginated)
       │                                      │
       │                              For each invoice:
       │                                      │
       └────────QueryInvoiceData─────→ Full invoice with line items
                                              │
                                    Product name + quantity + VTSZ
                                              │
                                    User's SKU→Packaging mapping (one-time)
                                              │
                                    Quantity × packaging weight = EPR data
                                              │
                                    Aggregate by KF code per quarter
                                              │
                                    CSV export → user submits to MOHU
```

### Implementation Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  NavOnlineSzamlaClient (Spring service)                     │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────┐ │
│  │ AuthService   │  │ XmlMarshaller│  │ SignatureService  │ │
│  │              │  │ (JAXB from   │  │ SHA-512 password  │ │
│  │ credentials  │  │  NAV XSDs)   │  │ SHA3-512 request  │ │
│  │ management   │  │              │  │ signature         │ │
│  └──────────────┘  └──────────────┘  └───────────────────┘ │
│                                                             │
│  Operations:                                                │
│  - queryTaxpayer(taxNumber) → TaxpayerInfo                 │
│  - queryInvoiceDigest(dateRange, direction) → [InvoiceSummary]│
│  - queryInvoiceData(invoiceNumber) → InvoiceDetail         │
└─────────────────────────────────────────────────────────────┘
         │                    │
   Screening module      EPR module
   (QueryTaxpayer)       (QueryInvoice*)
```

### Mock Mode Implementation — Two Layers

No real NAV company or credentials are available at this time. To unblock development, mock mode needs to work at two levels:

#### Layer A: In-App Fixtures (Mock Client)

The `NavOnlineSzamlaClient` service will have a `mode` configuration:

| Mode | Behavior |
|------|----------|
| `mock` | Returns realistic Hungarian invoice data from embedded Java fixtures. No network calls. No XML. |
| `test` | Calls `api-test.onlineszamla.nav.gov.hu` with real test credentials |
| `live` | Calls `api.onlineszamla.nav.gov.hu` with production credentials |

Mock data should include:
- 5-10 realistic Hungarian companies as partners (with valid-format tax numbers)
- 20-50 invoices per quarter with realistic line items (DIY products with VTSZ codes)
- Both OUTBOUND (company's sales) and INBOUND (company's purchases) invoices
- Mix of payment methods, VAT rates, and invoice types

**Good for:** UI development, VerdictEngine testing, demo mode, accountant demo. Zero external dependencies.

#### Layer B: WireMock NAV Számla Simulator (Mock Server)

A WireMock (or lightweight Spring Boot) service that mimics the real NAV Online Számla API locally:

- Listens on `localhost:8089/invoiceService/v3/{operation}`
- Accepts proper XML requests matching the real NAV XSD schema (header, user, software blocks)
- Validates request structure (returns `GeneralErrorResponse` with `INVALID_REQUEST` for malformed XML — matching real NAV behavior)
- Verifies the `requestSignature` computation is correct (hardcoded test signing key)
- Returns realistic XML responses with the correct namespaces (`http://schemas.nav.gov.hu/OSA/3.0/api`, `http://schemas.nav.gov.hu/NTCA/1.0/common`)
- Runs in `docker-compose.dev.yml` alongside PostgreSQL

**Good for:** Validating the full integration code path — XML marshalling, JAXB deserialization, SHA-512/SHA3-512 signing, error handling, pagination. Tests the real `NavOnlineSzamlaClient` code without credentials.

**When to build:** Layer A first (unblocks all UI/demo work). Layer B when implementing Story 2.2.2 (the actual NavOnlineSzamlaClient with real XML plumbing).

#### Effort Addition

| Task | Effort | Notes |
|------|--------|-------|
| Layer A: Mock fixtures | 1 day | Already in the ~5 day estimate |
| Layer B: WireMock NAV simulator | 1 day | Additional. Returns canned XML responses, validates request structure. |
| **Revised total for Story 2.2.2** | **~6 days** | Was ~5 days, +1 day for WireMock simulator |

### `QueryTaxpayer` for Screening — Key Insight

This operation is **not representation-bound**. Any NAV Online Számla technical user can query **any** tax number and get:
- Company name
- Address
- Incorporation type (ORGANIZATION / SELF_EMPLOYED / TAXABLE_PERSON)
- VAT group membership status

This is weaker than the M2M Adózó API (no debt data) but it's **available today to anyone with NAV Online Számla credentials**. For the screening module, it provides:
- **Partner exists and is active** — basic verification
- **Company type** — distinguishes companies from sole traders
- **Address verification** — the registered address matches what the partner claims

Combined with demo data for the debt/insolvency signals, this creates a partially-real, partially-demo screening experience that's compelling for a demo.

### Effort Estimate

| Task | Effort | Notes |
|------|--------|-------|
| JAXB code generation from 5 XSDs | 2-3 hours | Maven plugin, one-time setup |
| SignatureService (SHA-512 + SHA3-512) | 4 hours | Core crypto, well-documented in spec |
| NavOnlineSzamlaClient (HTTP + XML) | 1 day | Spring WebClient + JAXB marshal/unmarshal |
| Mock data fixtures — Layer A (in-app) | 1 day | Realistic Hungarian business data, Java objects |
| WireMock NAV simulator — Layer B (mock server) | 1 day | Validates XML/signing code path, docker-compose |
| QueryTaxpayer integration | Half day | Simplest operation, good first test |
| QueryInvoiceDigest + QueryInvoiceData | 1 day | Pagination, date filtering |
| Credential management UI + encrypted storage | 1 day | Settings page, AES-256 storage |
| **Total** | **~6 days** | Layer A unblocks UI work immediately; Layer B validates real integration code |

---

## Updated Summary

The three change proposals together form a coherent strategy:

| # | Proposal | Impact | Effort |
|---|----------|--------|--------|
| **CP-1** | Pivot to demo-first MVP, kill scraping, NAV M2M as future production path | Strategic direction | Sprint replan |
| **CP-2** | Rebrand Story 2.2 fictional adapters as "Demo Mode" | Minimal code change | Half day |
| **CP-3** | Build NAV Online Számla integration (real API, two-layer mock: in-app fixtures + WireMock simulator) | New technical foundation serving both screening (QueryTaxpayer) and EPR (QueryInvoice*) | ~6 days |

**The NAV Online Számla integration is the single most valuable piece of infrastructure** because it serves both product modules:
- **Screening:** `QueryTaxpayer` gives real partner verification today
- **EPR:** `QueryInvoiceDigest` + `QueryInvoiceData` provides full invoice data for EPR calculations
- **Shared auth:** Same technical user credentials power both modules
- **The accountant story:** When an accountant connects their NAV credentials, BOTH modules light up simultaneously

*Generated by Correct Course workflow on 2026-03-12*
