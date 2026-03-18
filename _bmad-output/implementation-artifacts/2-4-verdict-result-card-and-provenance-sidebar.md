# Story 2.4: Verdict Result Card & Provenance Sidebar

Status: done

## Story

As a User,
I want to see the final verdict in a high-contrast card with a sidebar showing exact data timestamps,
so that I can trust the freshness of the data.

## Acceptance Criteria

1. **Given** a completed search, **When** the verdict is returned to the UI, **Then** the UI shows an Emerald Shield (Reliable), Rose Shield (At-Risk), or Grey Shield (Stale/Unavailable) using the `TheShieldCard` component with a large 80px shield icon, status text in Hungarian (primary) with English label, the tax number in JetBrains Mono, and the truncated SHA-256 hash with copy-on-click.

2. **Given** a verdict with provenance data, **When** the Verdict Detail page renders, **Then** a Provenance Sidebar lists each data source name, its "Last Checked" time in relative format (e.g., "2 perccel ezelott" / "2 minutes ago"), and a status icon (checkmark for available, X for unavailable, clock for stale).

3. **Given** a verdict with status `TAX_SUSPENDED`, **When** the Shield Card renders, **Then** an "Amber" warning badge is displayed prominently indicating the suspended tax number requires manual review.

4. **Given** a verdict where data age exceeds the Freshness Guard threshold (48h from `risk-guard-tokens.json`), **When** the Shield Card renders, **Then** the Grey Shield is automatically displayed with a "Stale" warning banner regardless of the underlying verdict status.

5. **Given** any verdict display, **Then** an "Informational Purpose Only" liability disclaimer is rendered below the verdict card and provenance sidebar, clarifying that data originates from third-party government sources.

6. **Given** the verdict detail page on desktop, **Then** the Shield Card occupies 60% of content width (left) and the Provenance Sidebar occupies 40% (right), following the UX Spec section 11.3 layout.

7. **Given** the verdict detail page on mobile (< 768px), **Then** the Shield Card renders full-width at the top, and the Provenance Sidebar collapses into an expandable accordion section labeled "Adatforrasok reszletei" (Data source details).

8. **Given** the Shield Card, **Then** two action buttons are displayed: "Export PDF" as Primary button (disabled with tooltip "Coming in a future release" since PDF export is Story 5.1) and "Add to Watchlist" as Secondary button (disabled with tooltip "Coming in a future release" since watchlist is Story 3.6).

9. **Given** the verdict includes `riskSignals` from Story 2-3.5, **When** the Shield Card renders, **Then** a "Risk Signals" section displays human-readable explanations for each signal (e.g., "PUBLIC_DEBT_DETECTED" renders as "Active public debt detected").

10. **Given** a cached verdict (where `cached: true`), **When** the Shield Card renders, **Then** a subtle "Cached result" indicator is shown, and if `riskSignals` is empty, a note reads "Risk signal details not available for cached results."

## Tasks / Subtasks

- [x] **Task 1: Extend VerdictResponse API and Frontend Types** (AC: #1, #2, #9, #10)
  - [x] 1.1 Add new backend endpoint `GET /api/v1/screenings/{verdictId}/snapshot` returning `CompanySnapshotResponse` with source availability details parsed from JSONB (adapter names, `available` flag, `checked_at` per source)
  - [x] 1.2 Update `VerdictResponse` to include `companyName` (from snapshot) and `sha256Hash` (from verdict record) fields
  - [x] 1.3 Update `frontend/types/api.d.ts` to add `riskSignals`, `cached`, `sha256Hash`, `companyName` fields to `VerdictResponse` and add `SnapshotDetailResponse` interface with per-source provenance data
  - [x] 1.4 Update screening Pinia store to fetch snapshot details after verdict loads

- [x] **Task 2: Create VerdictCard (TheShieldCard) Component** (AC: #1, #3, #4, #8, #9, #10)
  - [x] 2.1 Create `frontend/app/components/Screening/VerdictCard.vue` with shield icon (80px), status-colored border (2px Emerald/Crimson/Amber/Grey per UX card system), tax number in JetBrains Mono, SHA-256 hash with copy-on-click
  - [x] 2.2 Implement status-to-color mapping: RELIABLE -> Emerald (#15803D border + Shield-Check icon), AT_RISK -> Crimson (#B91C1C border + Shield-X icon), INCOMPLETE/UNAVAILABLE -> Grey (Slate border + Shield-Clock icon), TAX_SUSPENDED -> Amber (#B45309 border + Shield-Alert icon)
  - [x] 2.3 Add Amber warning badge for TAX_SUSPENDED status with "Manual Review Required" text
  - [x] 2.4 Add Freshness Guard: if confidence is UNAVAILABLE (data > 48h), force Grey Shield display with "Stale data" warning banner regardless of status
  - [x] 2.5 Add Risk Signals section mapping enum codes to human-readable i18n labels
  - [x] 2.6 Add cached result indicator when `cached: true`
  - [x] 2.7 Add disabled "Export PDF" (Primary) and "Add to Watchlist" (Secondary) buttons with future-release tooltips
  - [x] 2.8 Create co-located `VerdictCard.spec.ts`

- [x] **Task 3: Create ProvenanceSidebar Component** (AC: #2, #4, #6, #7)
  - [x] 3.1 Create `frontend/app/components/Screening/ProvenanceSidebar.vue` listing each data source with name, relative timestamp (using `useDateRelative` composable pattern), status icon, and source URL as tertiary link
  - [x] 3.2 Implement desktop layout: fixed sidebar at 40% width with source list
  - [x] 3.3 Implement mobile layout: collapsible PrimeVue Accordion with label "Adatforrasok reszletei"
  - [x] 3.4 Add Freshness indicator at bottom: FRESH (Emerald), STALE (Amber), UNAVAILABLE (Grey)
  - [x] 3.5 Create co-located `ProvenanceSidebar.spec.ts`

- [x] **Task 4: Create Verdict Detail Page** (AC: #5, #6, #7)
  - [x] 4.1 Create `frontend/app/pages/screening/[taxNumber].vue` as the Verdict Detail page
  - [x] 4.2 Implement desktop two-column layout: VerdictCard (60%) + ProvenanceSidebar (40%)
  - [x] 4.3 Implement mobile single-column layout with VerdictCard on top and accordion provenance below
  - [x] 4.4 Add liability disclaimer below both columns in Slate 500 small text
  - [x] 4.5 Add "Back to Dashboard" navigation link in breadcrumb area
  - [x] 4.6 Load verdict data from screening store or fetch by taxNumber route param

- [x] **Task 5: Update Dashboard to Navigate to Verdict Detail** (AC: #1)
  - [x] 5.1 Replace the inline verdict display in `pages/dashboard/index.vue` with navigation to `/screening/[taxNumber]` after search completes
  - [x] 5.2 Ensure screening store retains verdict data across page navigation

- [x] **Task 6: Add i18n Keys** (AC: all)
  - [x] 6.1 Add verdict card keys to `hu/screening.json` and `en/screening.json`: shield labels, risk signal descriptions, provenance labels, disclaimer text, button labels, cached indicator, freshness labels
  - [x] 6.2 Ensure alphabetical key ordering in both locale files

- [x] **Task 7: Create useDateRelative Composable** (AC: #2)
  - [x] 7.1 Create `frontend/app/composables/formatting/useDateRelative.ts` returning locale-aware relative time strings ("2 perccel ezelott" / "2 minutes ago") using `Intl.RelativeTimeFormat`
  - [x] 7.2 Create co-located test file

- [x] **Task 8: Backend — Snapshot Provenance Endpoint** (AC: #2)
  - [x] 8.1 Add `GET /api/v1/screenings/snapshots/{snapshotId}/provenance` endpoint to `ScreeningController` returning parsed source availability from snapshot JSONB
  - [x] 8.2 Create `ProvenanceResponse` DTO: list of `SourceProvenance(sourceName, available, checkedAt, sourceUrl)` records
  - [x] 8.3 Add `getSnapshotProvenance(UUID snapshotId, UUID tenantId)` method to `ScreeningService`
  - [x] 8.4 Add `findSnapshotById(UUID snapshotId, UUID tenantId)` to `ScreeningRepository`
  - [x] 8.5 Write integration tests for provenance endpoint

- [x] **Task 9: Tests** (AC: all)
  - [x] 9.1 VerdictCard.spec.ts: renders correct shield color/icon for each status, shows risk signals, shows cached indicator, shows TAX_SUSPENDED badge, forces Grey Shield on UNAVAILABLE confidence, shows disabled action buttons
  - [x] 9.2 ProvenanceSidebar.spec.ts: renders source list with timestamps, shows correct status icons, collapses to accordion on mobile viewport, shows freshness indicator
  - [x] 9.3 Verdict Detail page integration: two-column desktop layout, single-column mobile layout, disclaimer visible, navigation from dashboard
  - [x] 9.4 Backend: ScreeningControllerTest for provenance endpoint, ScreeningServiceIntegrationTest for snapshot provenance retrieval

### Review Follow-ups (AI) — Round 1

- [x] [AI-Review][HIGH] Remove resize listener on unmount to avoid memory leak in verdict detail page. [frontend/app/pages/screening/[taxNumber].vue:24-30]
- [x] [AI-Review][MEDIUM] Consolidate duplicate onMounted hooks to avoid ordering fragility. [frontend/app/pages/screening/[taxNumber].vue:14-30]
- [x] [AI-Review][MEDIUM] Remove or document unused tenantId parameter in getSnapshotProvenance (TenantContext already used). [backend/src/main/java/hu/riskguard/screening/domain/ScreeningService.java:220]
- [x] [AI-Review][MEDIUM] Replace hardcoded 6h stale threshold with values from risk-guard-tokens.json. [frontend/app/components/Screening/ProvenanceSidebar.vue:22-36]
- [x] [AI-Review][MEDIUM] Move hardcoded source display names into i18n keys (screening.*) and include English translations. [frontend/app/components/Screening/ProvenanceSidebar.vue:68-77]
- [x] [AI-Review][MEDIUM] Add test coverage for stale-source clock icon when checkedAt exceeds freshness threshold. [frontend/app/components/Screening/ProvenanceSidebar.spec.ts:102-114]
- [x] [AI-Review][LOW] Remove unused _freshnessTokens import/variable or use it meaningfully. [frontend/app/components/Screening/VerdictCard.vue:5,105]
- [x] [AI-Review][LOW] Avoid dynamic class string replace() in template; add explicit textClass in statusConfig. [frontend/app/components/Screening/VerdictCard.vue:125]
- [x] [AI-Review][LOW] Add verdict detail page spec to match Task 9.3 claim (desktop/mobile layout, disclaimer, navigation). [frontend/app/pages/screening/[taxNumber].vue]
- [x] [AI-Review][LOW] Use user-friendly link text instead of raw URL for source links. [frontend/app/components/Screening/ProvenanceSidebar.vue:127,197]

### Review Follow-ups (AI) — Round 2

- [x] [AI-Review][HIGH] Copy hash button: remove corrupt dead-code label expression, add aria-label for accessibility, add type="button" to prevent accidental form submission. [frontend/app/components/Screening/VerdictCard.vue:209]
- [x] [AI-Review][MEDIUM] Source URLs always null in provenance response — add KNOWN_SOURCE_URLS static map keyed by adapter name to populate "View source" links for known government adapters. [backend/src/main/java/hu/riskguard/screening/domain/ScreeningService.java:232]
- [x] [AI-Review][MEDIUM] Dashboard watcher fires immediately on back-navigation with stale verdict — call clearSearch() on dashboard mount to reset store before watcher activates. [frontend/app/pages/dashboard/index.vue]
- [x] [AI-Review][MEDIUM] searchError not surfaced in verdict detail page — direct navigation + search failure silently fell through to "unavailable" state; added dedicated error state with message display. [frontend/app/pages/screening/[taxNumber].vue]
- [x] [AI-Review][MEDIUM] PrimeVue v4 Accordion requires value prop on Accordion root to control which panel is open — added value="provenance" to Accordion element in mobile layout. [frontend/app/components/Screening/ProvenanceSidebar.vue:84]
- [x] [AI-Review][LOW] "No risk signals" message used screening.verdict.reliable key — semantically wrong for non-RELIABLE statuses with empty signals; replaced with dedicated screening.riskSignals.noSignals key. [frontend/app/components/Screening/VerdictCard.vue:256]
- [x] [AI-Review][LOW] useDateRelative.useRelativeTime computed ref does not auto-tick — documented in JSDoc that it is locale-reactive only, not time-reactive. [frontend/app/composables/formatting/useDateRelative.ts]
- [x] [AI-Review][LOW] Mobile accordion test only checked container existence, not that source entries appeared inside accordion content — added two tests verifying source entries and freshness indicator render within mobile accordion. [frontend/app/components/Screening/ProvenanceSidebar.spec.ts]
- [x] [AI-Review][LOW] extractUuidClaim return value silently discarded in provenance endpoint — renamed to requireUuidClaim to make validation-only intent explicit. [backend/src/main/java/hu/riskguard/screening/api/ScreeningController.java:73]

## Dev Notes

### Critical Context — What This Story Changes

This is the **first full frontend feature story** in the project. Previous stories (2.1) created a basic SearchBar and SkeletonVerdictCard, and the dashboard currently shows a minimal inline verdict display with a comment: `<!-- Verdict result (basic display — full VerdictCard is Story 2.4) -->`. This story replaces that placeholder with the production-grade `TheShieldCard` component and adds the Verdict Detail page with Provenance Sidebar.

**What changes:**
- NEW: `VerdictCard.vue` — the core product component showing the Shield verdict
- NEW: `ProvenanceSidebar.vue` — data source transparency sidebar
- NEW: `pages/screening/[taxNumber].vue` — Verdict Detail page
- NEW: `composables/formatting/useDateRelative.ts` — relative time formatting
- NEW: Backend provenance endpoint exposing parsed JSONB source data
- MODIFIED: `pages/dashboard/index.vue` — navigate to detail page instead of inline display
- MODIFIED: `stores/screening.ts` — add snapshot detail fetching
- MODIFIED: `types/api.d.ts` — add new response types
- MODIFIED: `VerdictResponse.java` — add sha256Hash and companyName fields
- MODIFIED: `ScreeningController.java` — add provenance endpoint

**What is NOT in this story:**
- PDF Export (Story 5.1) — button shown but disabled
- Add to Watchlist (Story 3.6) — button shown but disabled
- "Alert me when source returns" queued retry (Epic 3)
- SEO gateway stubs for `/company/[taxNumber]` (Story 2.6) — this is `/screening/[taxNumber]` for authenticated users
- Full design system tokens (Story 3.0a) — use existing Tailwind utilities matching the UX color palette

### Existing Code You MUST Understand Before Touching

| File | Path | Why It Matters |
|---|---|---|
| dashboard/index.vue | frontend/app/pages/dashboard/index.vue | **MODIFYING.** Currently has inline verdict display (lines 49-74). Replace with navigation to Verdict Detail page. Keep SearchBar and SkeletonVerdictCard as-is. |
| screening.ts store | frontend/app/stores/screening.ts | **MODIFYING.** Currently has `search()` action returning VerdictResponse. Add `fetchProvenance(snapshotId)` action and `currentProvenance` state. |
| SkeletonVerdictCard.vue | frontend/app/components/Screening/SkeletonVerdictCard.vue | **READ-ONLY.** Shows the skeleton loading pattern. Your VerdictCard replaces this after data loads. Follow its component patterns (PrimeVue imports, i18n, Tailwind classes). |
| SearchBar.vue | frontend/app/components/Screening/SearchBar.vue | **READ-ONLY.** Triggers `screeningStore.search()`. After search completes, dashboard should navigate to the new detail page. |
| VerdictResponse.java | backend/src/main/java/hu/riskguard/screening/api/dto/VerdictResponse.java | **MODIFYING.** Add `sha256Hash` and `companyName` fields. Update `from()` factory. |
| ScreeningController.java | backend/src/main/java/hu/riskguard/screening/api/ScreeningController.java | **MODIFYING.** Add provenance endpoint. Follow existing extractUuidClaim pattern for auth. |
| ScreeningRepository.java | backend/src/main/java/hu/riskguard/screening/internal/ScreeningRepository.java | **MODIFYING.** Add findSnapshotById query. Follow existing jOOQ + tenantCondition pattern. |
| api.d.ts | frontend/types/api.d.ts | **MODIFYING.** Add missing fields to VerdictResponse, add SnapshotDetailResponse. Note: currently missing `riskSignals` and `cached` fields added in Story 2-3.5. |
| risk-guard-tokens.json | frontend/app/risk-guard-tokens.json | **READ-ONLY.** Contains freshness thresholds (freshThresholdHours: 6, unavailableThresholdHours: 48). Import for Freshness Guard logic. |

### DANGER ZONES — Common LLM Mistakes to Avoid

1. **DO NOT create a separate composable for verdict color mapping.** The color/icon mapping belongs IN the VerdictCard component as a computed property, not extracted to a composable. Only composables for reusable cross-component concerns (like date formatting).

2. **DO NOT hardcode freshness thresholds.** Import from `risk-guard-tokens.json` (already exists at `frontend/app/risk-guard-tokens.json`). The 48h threshold for forcing Grey Shield comes from `unavailableThresholdHours`.

3. **DO NOT use PrimeVue Toast for the disclaimer.** The disclaimer is a permanent, always-visible section below the verdict — not a dismissible notification. Use a styled `<div>` in Slate 500 text.

4. **DO NOT create new i18n key namespaces.** All keys go under the existing `screening.*` namespace in `hu/screening.json` and `en/screening.json`. Follow alphabetical ordering.

5. **DO NOT use Options API.** All components MUST use `<script setup lang="ts">` Composition API. Follow the existing pattern in SearchBar.vue and SkeletonVerdictCard.vue.

6. **DO NOT manually define TypeScript interfaces for API data in components.** Import from `~/types/api.d.ts`. Update the api.d.ts file if fields are missing.

7. **DO NOT log raw tax numbers in the frontend.** Console statements are stripped in production (`drop: ['console']`), but follow the pattern anyway — never include PII in any log message.

8. **DO NOT use custom CSS classes.** Use Tailwind utility classes exclusively. The UX spec defines specific colors: Deep Navy (#0F172A), Forest Emerald (#15803D), Crimson Alert (#B91C1C), Amber (#B45309). Map these to Tailwind equivalents (emerald-700, rose-700, amber-600, slate-900).

9. **DO NOT forget to handle the `companyName` field.** The Shield Card needs to display the company name prominently. This comes from the snapshot JSONB — the `e-cegjegyzek` adapter returns `companyName` when available, or it may come from `nav-online-szamla` QueryTaxpayer response. Prefer the first available non-null source.

10. **DO NOT break the existing SkeletonVerdictCard.** It still shows during search loading. The VerdictCard replaces it AFTER data arrives. Ensure the transition is smooth — skeleton hidden when `currentVerdict` is populated.

### Project Structure Notes

**Frontend file structure follows architecture spec exactly:**
```
frontend/app/
  components/Screening/
    SearchBar.vue              # EXISTS (Story 2.1)
    SearchBar.spec.ts          # EXISTS (Story 2.1)
    SkeletonVerdictCard.vue    # EXISTS (Story 2.1)
    SkeletonVerdictCard.spec.ts # EXISTS (Story 2.1)
    VerdictCard.vue            # NEW — this story
    VerdictCard.spec.ts        # NEW — this story
    ProvenanceSidebar.vue      # NEW — this story
    ProvenanceSidebar.spec.ts  # NEW — this story
  composables/formatting/
    useDateRelative.ts         # NEW — this story
    useDateRelative.spec.ts    # NEW — this story
  pages/
    dashboard/index.vue        # MODIFY — remove inline verdict, add navigation
    screening/
      [taxNumber].vue          # NEW — Verdict Detail page
  stores/
    screening.ts               # MODIFY — add provenance fetching
  i18n/
    hu/screening.json          # MODIFY — add verdict card keys
    en/screening.json          # MODIFY — add verdict card keys
  types/
    api.d.ts                   # MODIFY — add missing fields

backend/src/main/java/hu/riskguard/screening/
  api/
    ScreeningController.java   # MODIFY — add provenance endpoint
    dto/
      VerdictResponse.java     # MODIFY — add sha256Hash, companyName
      ProvenanceResponse.java  # NEW — provenance DTO
  domain/
    ScreeningService.java      # MODIFY — add getSnapshotProvenance method
  internal/
    ScreeningRepository.java   # MODIFY — add findSnapshotById query
```

### Technical Requirements

**VerdictCard Component Structure:**
- Uses PrimeVue `Card` or custom div with the Verdict Card styling from UX Spec 10.3: 24px padding, 12px border-radius (`rounded-xl`), `shadow-md`, 2px status-colored border
- Shield icon: Use PrimeVue icon set (`pi pi-shield`) at 80px, or an SVG shield icon colored by status
- Tax number displayed in `font-mono` (JetBrains Mono) — project already has this configured
- SHA-256 hash: truncated to first 16 chars + "..." with a copy button using `navigator.clipboard.writeText()`
- Status text: primary Hungarian label from i18n, secondary English label in smaller grey text

**Status-to-Visual Mapping (CRITICAL):**

| Status | Shield Color | Border Color | Icon | Badge |
|--------|-------------|--------------|------|-------|
| RELIABLE | Emerald (#15803D) | 2px emerald-700 | pi-check-circle or Shield-Check | "Megbizhato" green pill |
| AT_RISK | Crimson (#B91C1C) | 2px rose-700 | pi-times-circle or Shield-X | "Kockazatos" red pill |
| INCOMPLETE | Grey (Slate) | 2px slate-400 | pi-clock or Shield-Clock | "Hianyos" grey pill |
| TAX_SUSPENDED | Amber (#B45309) | 2px amber-600 | pi-exclamation-triangle | "Adoszam felfuggesztve" amber pill |
| UNAVAILABLE | Grey (Slate) | 2px slate-400 | pi-clock or Shield-Clock | "Nem elerheto" grey pill |

**Confidence-to-Freshness Visual (Provenance Sidebar):**

| Confidence | Color | Label |
|-----------|-------|-------|
| FRESH | Emerald | "Friss" / "Fresh" |
| STALE | Amber | "Elavult" / "Stale" |
| UNAVAILABLE | Grey | "Nem elerheto" / "Unavailable" |

**Risk Signal Human-Readable Mapping:**

| Signal Code | Hungarian | English |
|------------|-----------|---------|
| PUBLIC_DEBT_DETECTED | Aktiv koztartozas eszlelve | Active public debt detected |
| INSOLVENCY_PROCEEDINGS_ACTIVE | Fizeteskeptelensegi eljaras folyamatban | Insolvency proceedings active |
| TAX_NUMBER_SUSPENDED | Adoszam felfuggesztve | Tax number suspended |
| DATA_EXPIRED | Az adatok lejarta | Data expired |
| SOURCE_UNAVAILABLE:{name} | Forras nem elerheto: {name} | Source unavailable: {name} |

**ProvenanceSidebar Data Structure:**
The sidebar consumes the provenance endpoint response. Each source entry shows:
- Source name (from adapter key, mapped to display name via i18n)
- Last checked timestamp in relative format
- Available/unavailable status icon
- Source URL as a tertiary (borderless) link if available

**Responsive Breakpoints (from UX Spec 8.2):**
- Mobile (< 768px): Single column, accordion provenance
- Tablet (768-1024px): Two columns but sidebar narrower
- Desktop (> 1024px): 60/40 split as specified

### Architecture Compliance

- All new components in `frontend/app/components/Screening/` — within the screening module's frontend boundary
- Backend endpoint follows REST pattern: `GET /api/v1/screenings/snapshots/{snapshotId}/provenance`
- ProvenanceResponse DTO is a Java record in `screening/api/dto/` with `static from()` factory method
- New repository method uses jOOQ with tenant_id condition (TenantContext pattern)
- No cross-module imports — all within screening module
- i18n keys under `screening.*` namespace only
- Frontend spec files co-located with components

### Library and Framework Requirements

**No new dependencies required.** This story uses only existing project dependencies:
- PrimeVue 4.5.4: Card, Button, Accordion, Tag components (already installed)
- Tailwind CSS 4.2.1: utility classes for layout and colors (already configured)
- Pinia 3.0.4: state management (already configured)
- Vue 3 Composition API with `<script setup lang="ts">`
- `Intl.RelativeTimeFormat` — native browser API for relative date formatting (no library needed)
- JetBrains Mono — already in project for data display (`font-mono` class)

### Testing Requirements

**VerdictCard.spec.ts:**
- Renders Emerald shield with checkmark icon for RELIABLE status
- Renders Crimson shield with X icon for AT_RISK status
- Renders Grey shield with clock icon for INCOMPLETE status
- Renders Amber shield with warning icon for TAX_SUSPENDED status
- Displays tax number in monospace font
- Displays truncated SHA-256 hash
- Copy button copies full hash to clipboard (mock navigator.clipboard)
- Shows risk signals section with human-readable labels
- Shows "Cached result" indicator when cached=true
- Shows "Risk signal details not available" when cached=true and riskSignals empty
- Forces Grey Shield when confidence is UNAVAILABLE regardless of status
- Shows disabled Export PDF and Add to Watchlist buttons with tooltips
- Shows TAX_SUSPENDED manual review badge

**ProvenanceSidebar.spec.ts:**
- Renders list of data sources with names and timestamps
- Shows checkmark icon for available sources
- Shows X icon for unavailable sources
- Shows clock icon for stale sources
- Displays relative timestamps correctly
- Shows freshness indicator at bottom
- Collapses to accordion on mobile viewport (use viewport mocking)

**Backend tests:**
- `ScreeningControllerTest`: provenance endpoint returns 200 with correct source list, returns 404 for unknown snapshot, respects tenant isolation
- `ScreeningServiceIntegrationTest`: getSnapshotProvenance parses JSONB correctly, handles empty/null snapshot data

### Previous Story Intelligence

**Story 2-3.5 established (MOST RECENT — read carefully):**
1. `riskSignals` field added to `VerdictResponse` — list of string reason codes like `["PUBLIC_DEBT_DETECTED", "SOURCE_UNAVAILABLE:nav-debt"]`
2. `cached` boolean field added to `VerdictResponse` — true when served from idempotency cache
3. `PartnerStatusChanged` event now published on status transitions — downstream notification module will consume this
4. Event is published OUTSIDE TX2 (after commit) — important for data consistency
5. `VerdictResponse.from(SearchResult)` uses `getLiteral()` for enum-to-string conversion

**Story 2-3 established:**
1. `VerdictEngine` is a pure function — RELIABLE/AT_RISK/INCOMPLETE/TAX_SUSPENDED with FRESH/STALE/UNAVAILABLE confidence
2. `SnapshotDataParser` parses JSONB snapshot into typed `SnapshotData` — your provenance endpoint can reuse this pattern
3. Freshness thresholds from `risk-guard-tokens.json`: 6h (fresh), 24h (stale boundary), 48h (unavailable)
4. Risk signals are populated in the VerdictResult and propagated to VerdictResponse
5. All 197+ tests pass. Do not regress.

**Story 2.2 established:**
1. Snapshot JSONB structure per adapter: `{ "adapter-name": { "available": true/false, "data": {...} } }`
2. DemoCompanyDataAdapter returns realistic fixture data with adapter keys: `demo-company-data` (or similar)
3. CompanyDataAggregator orchestrates parallel fetching via Virtual Threads
4. `data_source_mode` enum (DEMO/TEST/LIVE) stored in company_snapshots table

**Story 2.1 established:**
1. SearchBar.vue with Zod validation and auto-formatting
2. SkeletonVerdictCard.vue with PrimeVue Skeleton components
3. Screening Pinia store with `search()` action
4. Dashboard page at `pages/dashboard/index.vue` with inline verdict display placeholder
5. i18n keys in `screening.*` namespace

**Key patterns from existing frontend code:**
- `useI18n()` for translations (auto-imported in Nuxt)
- `useScreeningStore()` for state management
- `storeToRefs()` for reactive store access in templates
- PrimeVue components imported directly: `import Button from 'primevue/button'`
- Tailwind for all styling, no custom CSS
- `data-testid` attributes on key elements for test targeting

### References

- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#11.3] Verdict Detail Page layout with Shield Card and Provenance Sidebar
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#6.1] TheShieldCard critical component specification
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#3.1] Color system: Deep Navy, Forest Emerald, Crimson Alert, Amber
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#7.2] Feedback patterns: Emerald for success, Amber for warnings, Crimson for errors
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#10.3] Card system: Verdict Card padding 24px, rounded-xl, shadow-md, 2px status border
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#8.1-8.2] Responsive breakpoints and Dual-Context strategy
- [Source: _bmad-output/planning-artifacts/architecture.md#ADR-4] Data source adapter architecture with CompanyDataPort
- [Source: _bmad-output/planning-artifacts/architecture.md#Cross-Cutting] Data Freshness tiered model (6/24/48h)
- [Source: _bmad-output/planning-artifacts/architecture.md#Frontend-Structure] Component organization in components/Screening/
- [Source: _bmad-output/planning-artifacts/epics.md#Story-2.4] Original story AC with shield colors and provenance
- [Source: _bmad-output/project-context.md] Project rules: Composition API, co-located specs, no hardcoded strings, JetBrains Mono for data
- [Source: _bmad-output/implementation-artifacts/2-3.5-verdict-status-change-events-and-dto-enrichment.md] riskSignals and cached field additions to VerdictResponse
- [Source: _bmad-output/implementation-artifacts/2-3-deterministic-verdict-state-machine.md] VerdictEngine evaluation order, SnapshotDataParser JSONB structure

## Dev Agent Record

### Agent Model Used

gitlab/duo-chat-sonnet-4-6 (Claude 3.5 Sonnet via GitLab Duo) — initial implementation
gitlab/duo-chat-opus-4-6 (Claude Opus via GitLab Duo) — review follow-up fixes

### Debug Log References

No blocking issues encountered. All tests passed on first run after two minor test fixes:
1. VerdictCard: `v-if` wrapper on Risk Signals section was too aggressive (hid the section when `cached=true` and `riskSignals=[]`). Fixed by removing the outer `v-if`.
2. ProvenanceSidebar: Test fixture used a fixed `checkedAt` timestamp that was >6h old at test runtime. Fixed by using `new Date().toISOString()` for FRESH sources.

### Completion Notes List

- **Backend**: Extended `SnapshotData` record with optional `companyName` field; added 4-arg convenience constructor for backward compat with VerdictEngineTest. `SnapshotDataParser.parse()` now extracts `companyName` from the first adapter that provides it.
- **Backend**: Updated `SearchResult` record with `companyName` and `sha256Hash` fields. `ScreeningService.search()` propagates both to the `VerdictResponse`. `writeAuditLog()` now returns the computed hash.
- **Backend**: Added `GET /api/v1/screenings/snapshots/{snapshotId}/provenance` endpoint returning per-source availability from snapshot JSONB. New `ProvenanceResponse` DTO with nested `SourceProvenance` record. Follows existing tenant-isolation pattern via `TenantContext`.
- **Backend**: Added `findSnapshotById(UUID, UUID)` to `ScreeningRepository` using jOOQ type-safe DSL with tenant_id scoping. Added `SnapshotRecord` inner type for the query result.
- **Backend**: `ScreeningService.getSnapshotProvenance()` parses snapshot JSONB via `SnapshotDataParser` and builds `ProvenanceResponse`. Uses a static `ObjectMapper` (not Spring-managed to avoid module-crossing).
- **Frontend**: Updated `api.d.ts` to add `riskSignals`, `cached`, `companyName`, `sha256Hash` to `VerdictResponse`, and new `SourceProvenanceEntry` / `SnapshotProvenanceResponse` interfaces.
- **Frontend**: Updated screening Pinia store to add `currentProvenance` state and `fetchProvenance()` action. Store auto-fetches provenance after verdict loads.
- **Frontend**: Created `useDateRelative` composable using native `Intl.RelativeTimeFormat` — no new dependencies. Returns locale-aware relative timestamps ("2 perccel ezelőtt" / "2 minutes ago").
- **Frontend**: Created `VerdictCard.vue` with full status-to-visual mapping (RELIABLE→Emerald, AT_RISK→Crimson, TAX_SUSPENDED→Amber, INCOMPLETE/UNAVAILABLE→Grey). Freshness Guard forces Grey Shield when `confidence === 'UNAVAILABLE'`. Risk Signals section with i18n label mapping. SHA-256 copy-on-click. Disabled action buttons with tooltips. All DANGER ZONE anti-patterns avoided.
- **Frontend**: Created `ProvenanceSidebar.vue` with desktop list + mobile PrimeVue Accordion layout controlled by `mobile` prop. Freshness indicator at bottom in Emerald/Amber/Slate colors. `useDateRelative` for relative timestamps.
- **Frontend**: Created `pages/screening/[taxNumber].vue` as Verdict Detail page. 60/40 desktop split, single-column mobile with accordion provenance. Liability disclaimer below content. Back to Dashboard breadcrumb. Auto-fetches data if navigated directly.
- **Frontend**: Updated `pages/dashboard/index.vue` — removed inline verdict display placeholder, added `watch(currentVerdict)` to navigate to `/screening/[taxNumber]` after search completes.
- **i18n**: Added all verdict card keys to `hu/screening.json` and `en/screening.json` under `screening.*` namespace (actions, disclaimer, freshness, provenance, riskSignals, verdict sections). Alphabetical ordering maintained.
- **Tests**: 85 frontend tests pass (7 test files). Full backend test suite passes (BUILD SUCCESSFUL in 3m 1s). TypeScript `tsc --noEmit` clean.

**Review Follow-up Fixes (2026-03-13):**
- ✅ Resolved review finding [HIGH]: Consolidated duplicate `onMounted` hooks into a single hook and added `onUnmounted` with `removeEventListener('resize')` to prevent memory leak in verdict detail page.
- ✅ Resolved review finding [MEDIUM]: Removed unused `tenantId` parameter from `ScreeningService.getSnapshotProvenance()` — tenant isolation is enforced by `TenantContext` in repository layer. Updated controller, unit tests, and integration tests.
- ✅ Resolved review finding [MEDIUM]: Replaced hardcoded 6h stale threshold with `tokens.freshness.freshThresholdHours` from `risk-guard-tokens.json` in `ProvenanceSidebar.vue`.
- ✅ Resolved review finding [MEDIUM]: Moved hardcoded source display names to i18n keys (`screening.provenance.sources.*`) with both Hungarian and English translations.
- ✅ Resolved review finding [MEDIUM]: Added test case for stale-source clock icon when `checkedAt` exceeds freshness threshold (7h-old timestamp → amber clock icon).
- ✅ Resolved review finding [LOW]: Removed unused `_freshnessTokens` variable and `tokens` import from `VerdictCard.vue`.
- ✅ Resolved review finding [LOW]: Added explicit `textClass` property to `statusConfig` computed, replacing fragile `pillClass.replace()` dynamic class manipulation in template.
- ✅ Resolved review finding [LOW]: Created comprehensive verdict detail page spec (`[taxNumber].spec.ts`) with 11 tests covering desktop/mobile layout, disclaimer, navigation, resize cleanup, and data fetching.
- ✅ Resolved review finding [LOW]: Replaced raw URL display text with i18n-based "View source" link text and external-link icon in `ProvenanceSidebar.vue`.
- **Tests**: 97 frontend tests pass (8 test files). Full backend BUILD SUCCESSFUL. TypeScript `tsc --noEmit` clean.

### Change Log

- 2026-03-13: Story 2.4 implemented — Verdict Result Card & Provenance Sidebar. Backend provenance endpoint + extended VerdictResponse. Frontend VerdictCard, ProvenanceSidebar, useDateRelative composable, [taxNumber].vue detail page, dashboard navigation. Full test coverage.
- 2026-03-13: Addressed code review findings Round 1 — 10 items resolved (1 HIGH, 5 MEDIUM, 4 LOW). Memory leak fix, API cleanup, i18n compliance, token-driven thresholds, new page spec, improved code quality.
- 2026-03-13: Addressed code review findings Round 2 — 9 items resolved (1 HIGH, 4 MEDIUM, 4 LOW). Accessibility fix on copy button, source URLs now populated from KNOWN_SOURCE_URLS map for government adapters, dashboard stale-verdict redirect race condition fixed, search error surfaced in detail page, PrimeVue v4 Accordion value binding fixed, dedicated no-risk-signals i18n key, useRelativeTime documented as locale-reactive only, mobile accordion tests strengthened, requireUuidClaim rename for clarity. Frontend: 103 tests pass (8 files). Backend: BUILD SUCCESSFUL.

### File List

**Backend — Modified:**
- `backend/src/main/java/hu/riskguard/screening/domain/SnapshotData.java` — added `companyName` field + 4-arg convenience constructor
- `backend/src/main/java/hu/riskguard/screening/domain/SnapshotDataParser.java` — extract `companyName` from adapter JSONB
- `backend/src/main/java/hu/riskguard/screening/domain/ScreeningService.java` — added `getSnapshotProvenance()` method, `parseJsonbToMap()`, extended `SearchResult` with `companyName`/`sha256Hash`, extended `Tx2Result`
- `backend/src/main/java/hu/riskguard/screening/api/dto/VerdictResponse.java` — added `companyName`, `sha256Hash` fields + updated `from()` factory
- `backend/src/main/java/hu/riskguard/screening/api/ScreeningController.java` — added `GET /snapshots/{snapshotId}/provenance` endpoint
- `backend/src/main/java/hu/riskguard/screening/internal/ScreeningRepository.java` — added `findSnapshotById()`, `SnapshotRecord` inner type, `writeAuditLog()` now returns hash

**Backend — New:**
- `backend/src/main/java/hu/riskguard/screening/api/dto/ProvenanceResponse.java` — new DTO with `SourceProvenance` inner record and `from()` factory

**Backend — Tests Modified:**
- `backend/src/test/java/hu/riskguard/screening/api/ScreeningControllerTest.java` — updated `SearchResult` constructor calls (10→ args), added 3 provenance endpoint tests
- `backend/src/test/java/hu/riskguard/screening/ScreeningServiceIntegrationTest.java` — added 5 provenance + companyName/sha256Hash integration tests
- `backend/src/test/java/hu/riskguard/screening/domain/SnapshotDataParserTest.java` — added 5 companyName extraction tests

**Frontend — New:**
- `frontend/app/components/Screening/VerdictCard.vue`
- `frontend/app/components/Screening/VerdictCard.spec.ts` — 24 tests
- `frontend/app/components/Screening/ProvenanceSidebar.vue`
- `frontend/app/components/Screening/ProvenanceSidebar.spec.ts` — 15 tests
- `frontend/app/composables/formatting/useDateRelative.ts`
- `frontend/app/composables/formatting/useDateRelative.spec.ts` — 11 tests
- `frontend/app/pages/screening/[taxNumber].vue`

**Frontend — Modified:**
- `frontend/types/api.d.ts` — added `riskSignals`, `cached`, `companyName`, `sha256Hash` to `VerdictResponse`; added `SourceProvenanceEntry`, `SnapshotProvenanceResponse` interfaces
- `frontend/app/stores/screening.ts` — added `currentProvenance`, `isLoadingProvenance`, `provenanceError` state; added `fetchProvenance()` action; removed PII console.error
- `frontend/app/pages/dashboard/index.vue` — removed inline verdict placeholder, added watcher to navigate to verdict detail page
- `frontend/app/i18n/hu/screening.json` — added actions, disclaimer, freshness, provenance, riskSignals sections
- `frontend/app/i18n/en/screening.json` — added actions, disclaimer, freshness, provenance, riskSignals sections

**Frontend — New (Review Follow-ups Round 1):**
- `frontend/app/pages/screening/[taxNumber].spec.ts` — 11 tests for verdict detail page (layout, navigation, resize cleanup, data fetching)

**Frontend — Modified (Review Follow-ups Round 2):**
- `frontend/app/components/Screening/VerdictCard.vue` — copy button: removed dead-code expression, added aria-label + type="button"; no-risk-signals uses dedicated i18n key
- `frontend/app/components/Screening/VerdictCard.spec.ts` — added 3 tests: aria-label, type=button, INCOMPLETE no-signals message
- `frontend/app/components/Screening/ProvenanceSidebar.vue` — added value="provenance" to Accordion root for PrimeVue v4 compatibility
- `frontend/app/components/Screening/ProvenanceSidebar.spec.ts` — added 2 mobile accordion tests verifying source entries and freshness indicator within accordion content
- `frontend/app/composables/formatting/useDateRelative.ts` — documented useRelativeTime as locale-reactive only (not time-reactive)
- `frontend/app/pages/screening/[taxNumber].vue` — exposed searchError from store; added dedicated error state with message display
- `frontend/app/pages/screening/[taxNumber].spec.ts` — added 1 test for search error state; updated all beforeEach to reset mockSearchError
- `frontend/app/pages/dashboard/index.vue` — added onMounted clearSearch() to prevent stale verdict redirect on back-navigation
- `frontend/app/i18n/en/screening.json` — added copyHash, noSignals, searchFailed keys
- `frontend/app/i18n/hu/screening.json` — added copyHash, noSignals, searchFailed keys

**Backend — Modified (Review Follow-ups Round 2):**
- `backend/src/main/java/hu/riskguard/screening/domain/ScreeningService.java` — added KNOWN_SOURCE_URLS static map; getSnapshotProvenance now populates per-adapter source URLs for known government adapters
- `backend/src/main/java/hu/riskguard/screening/api/ScreeningController.java` — renamed extractUuidClaim → requireUuidClaim for clarity
- `backend/src/test/java/hu/riskguard/screening/ScreeningServiceIntegrationTest.java` — added getSnapshotProvenanceShouldPopulateKnownAdapterSourceUrls test; updated demo source assertion to verify null sourceUrl

**Project:**
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — status updated: ready-for-dev → in-progress → review → done
