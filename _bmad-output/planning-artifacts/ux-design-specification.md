---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14]
lastStep: 14
inputDocuments: 
  - "/home/andras/dev/risk_guard/_bmad-output/planning-artifacts/prd.md"
  - "/home/andras/dev/risk_guard/_bmad-output/planning-artifacts/product-brief-risk_guard-2026-03-04.md"
  - "/home/andras/dev/risk_guard/_bmad-output/planning-artifacts/research/technical-Scraper-Tech-Audit-research-2026-03-04.md"
  - "/home/andras/dev/risk_guard/_bmad-output/planning-artifacts/research/market-Hungarian-SME-behavior-and-red-flag-response-research-2026-03-04.md"
  - "/home/andras/dev/risk_guard/_bmad-output/project-context.md"
  - "/home/andras/dev/risk_guard/_bmad-output/planning-artifacts/architecture.md"
---

# UX Design Specification risk_guard

**Author:** Andras
**Date:** 2026-03-05

---

## 1. Executive Summary

### 1.1 Project Vision
risk_guard (PartnerRadar) is a high-integrity B2B SaaS platform for Hungarian SMEs and accountants. It provides deterministic partner risk screening, automated EPR compliance, and **court-ready due diligence proof**. The system acts as a "Compliance Vault," allowing users to monitor partner portfolios and generate immutable evidence of risk-mitigation for NAV audits.

### 1.2 Target Users
- **Decision Makers (SME Owners):** Manage portfolios of up to 100 partners. They need mobile-responsive lists with algorithmic prioritization of "Red" (At-Risk) and "Grey" (Unavailable) statuses.
- **Precision Operators (Accountants):** Desktop-first power users managing 40+ client portfolios. They require high-volume efficiency and batch processing.
- **System Admins:** Technical users monitoring scraper success rates and DOM integrity.

---

## 2. Core User Experience

### 2.1 Defining Experience: "The Compliance Pulse"
The defining experience of risk_guard is the **"Risk Pulse Dashboard."** For authenticated users, the interaction begins with a prioritized view of their partner ecosystem. The system transforms from a lookup tool into a passive monitoring shield that only demands attention when a status deviates.

### 2.2 User Mental Model: "Inventory & Monitor"
- **Risk:** "My partners are a list I need to keep green."
- **EPR:** "My packaging is a catalog where I just need to record weights."

### 2.3 Success Criteria
- **Zero-Noise Monitoring:** Alerts only on actual status changes.
- **Effortless EPR:** Generating a MOHU report takes < 2 minutes post-setup.
- **Outage Transparency:** 100% clarity on data age during portal downtime.

---

## 3. Visual Design Foundation: "The Safe Harbor"

### 3.1 Color System
- **Authority (Primary):** Deep Navy (#0F172A)
- **Reliable (Success):** Forest Emerald (#15803D)
- **At-Risk (Danger):** Crimson Alert (#B91C1C)
- **Stale/Outage (Warning):** Amber (#B45309)

### 3.2 Typography
- **Primary:** **Inter** (Sans-Serif) for legibility.
- **Data:** **JetBrains Mono** for Tax IDs and Hashes.

---

## 4. Design Direction: The "Vault" Pivot

### 4.1 Transition Strategy
A sharp visual and functional transition between the public "Gateway" and the private "Professional Workspace."
- **Public:** Airy, horizontal, marketing-focused.
- **Private:** High-density, sidebar-driven, "Sober/Legal" aesthetic.

---

## 5. User Journey Flows

### 5.1 The Morning Risk Pulse
Automated promotion of status changes to the top of the dashboard with one-tap access to the **Audit Proof PDF**.

### 5.2 The EPR Reporting Cycle
Inventory-based weights entry with seasonal template support and "Copy from Previous" logic.

### 5.3 Outage Resilience
Displaying the **Grey Shield** with stale data and explicit timestamps when government sources are offline.

---

## 6. Page & Component Strategy

### 6.1 Critical Components
- **`TheShieldCard`:** Authoritative verdict anchor with integrity hashes.
- **`AuditDispatcher`:** Native mobile share integration for legal proofs.
- **`MaterialInventoryBlock`:** High-speed weight entry grid for EPR.
- **`ContextGuard`:** Safety interstitial for multi-tenant switching.

### 6.2 Page Map
- **Landing Page:** Zero-friction Tax ID search.
- **Risk Pulse Dashboard:** Prioritized partner monitoring.
- **Flight Control:** Accountant's aggregate client view.
- **Mission Control:** Impact-driven scraper health monitoring for admins.

---

## 7. UX Consistency Patterns

### 7.1 Button Hierarchy
- **Primary (Authority):** Deep Navy (#0F172A). For terminal actions (Generate Proof, Submit).
- **Secondary (Utility):** Slate Grey border (#64748B). For management (Add to Watchlist).
- **Tertiary (Evidence):** Borderless Slate. For deep-linking to sources.

### 7.2 Feedback Patterns
- **Integrity Validation (Success):** Emerald Green + SHA-256 Badge.
- **Regulatory Warning (Amber):** Amber Banner for stale data.
- **Immediate Correction (Error):** Crimson Red for validation failures.

### 7.3 Form & Validation
- **The "MOHU Gate":** Real-time weight validation enforcing decimal precision.
- **Intelligent Masking:** Auto-formatting for 8/11 digit Hungarian Tax Numbers.

### 7.4 Loading & Empty States
- **Skeletal Trust:** Layout-mimicking Skeletons during scraper fetches.
- **Interstitial Safety:** Full-screen "Context Guard" during tenant switches.

---

## 8. Responsive Design & Accessibility

### 8.1 Responsive Strategy
risk_guard utilizes a **"Dual-Context"** responsive strategy to serve distinct user behaviors:
- **Mobile (Decision Context):** Optimized for one-handed thumb interaction. Priority is given to the "Shield Verdict" and "Audit Dispatcher." Navigation uses a bottom-sheet pattern for secondary partner data.
- **Desktop (Operation Context):** Optimized for mouse/keyboard efficiency. Priority is given to multi-tenant aggregation, dense data grids, and batch EPR reporting.

### 8.2 Breakpoint Strategy
- **Mobile (< 768px):** Single-column layout; card-based data display; centered primary verdicts.
- **Tablet (768px - 1024px):** Hybrid layout; sidebar collapses to icons; 2-column dashboard grids.
- **Desktop (> 1024px):** Persistent sidebar; multi-column "Quiet Grid" tables; expanded side-panel summaries for EPR.

### 8.3 Accessibility Strategy (WCAG 2.1 AA)
- **Contrast:** Strict 4.5:1 ratio for all metadata; 7:1 for primary verdicts.
- **Redundancy:** All status colors are paired with unique icons (Shield-Check, Shield-X, Shield-Clock).
- **Navigation:** Skip-links for accountants to bypass sidebars; logical tab order through the "MOHU Gate" weight inputs.
- **Screen Readers:** ARIA-live regions for asynchronous scraper status updates.

### 8.4 Testing Strategy
- **Cross-Browser:** Automated CI testing on Chromium (Desktop) and WebKit (Mobile) via Playwright.
- **A11y Audit:** Weekly **Pa11y** or **Axe-core** scans of core user journeys.
- **Real-World Sim:** Performance testing on throttled 3G networks to ensure "Behind-the-Glass" skeletons work effectively.

---

## 9. Implementation Roadmap

### 9.1 Phase 1: Core Vault (MVP)
- Implement `TheShieldCard` and `SearchBar` auto-masking.
- Scaffold multi-tenant `ContextGuard` and Dashboard shell.
- Establish "The Safe Harbor" color tokens in Tailwind.

### 9.2 Phase 2: Compliance Center
- Implement `MaterialInventoryBlock` and seasonal templates.
- Integrate `AuditDispatcher` with native mobile sharing.
- Build "Flight Control" aggregate view for accountants.

### 9.3 Phase 3: Solo-Dev Resilience
- Build "Mission Control" health gauges and MTBF scoring.
- Finalize WCAG 2.1 AA accessibility audit and keyboard mapping.

---

## 10. Layout Grid & Spacing System

The spacing system is the invisible architecture that makes risk_guard feel "right." Every measurement below uses Tailwind's default 4px base unit, ensuring consistency across all components without custom CSS.

### 10.1 Spacing Scale

| Token | Value | Usage |
|---|---|---|
| `space-1` | 4px | Inline icon gaps, badge padding |
| `space-2` | 8px | Form field internal padding, tight groups |
| `space-3` | 12px | Card internal padding (compact), list item spacing |
| `space-4` | 16px | Standard card padding, section gaps within a card |
| `space-6` | 24px | Gap between cards, major section breaks |
| `space-8` | 32px | Page section separation |
| `space-12` | 48px | Landing page section spacing (airy mode) |
| `space-16` | 64px | Landing page hero vertical padding |

**Density Rule:** The private workspace (post-login) uses `space-3` to `space-6` as its primary rhythm. The public landing page uses `space-8` to `space-16` for its airy feel. This single shift in spacing creates the "Vault Pivot" density transition without changing any components.

### 10.2 Layout Anatomy

#### Sidebar (Desktop, > 1024px)
- **Width expanded:** 240px
- **Width collapsed (tablet 768–1024px):** 64px (icon-only)
- **Background:** Slate 900 (`#0F172A`)
- **Items:** 40px height, 12px left padding, 8px icon-to-label gap
- **Active indicator:** 3px Indigo 600 left border + Slate 800 background
- **Navigation sections:** Dashboard, Watchlist, EPR, Admin (role-gated), separated by subtle Slate 800 dividers

#### Top Bar
- **Height:** 56px
- **Background:** White with 1px Slate 200 bottom border
- **Left:** Hamburger (mobile) or breadcrumb trail (desktop)
- **Center (mobile only):** Page title
- **Right:** Context Switcher (accountant), Language toggle, User avatar + dropdown

#### Content Area
- **Max-width:** 1280px, centered with `auto` margins
- **Padding:** 24px (desktop), 16px (tablet), 12px (mobile)
- **Background:** Slate 50 (`#F8FAFC`) — subtle grey that makes white cards pop

### 10.3 Card System

Cards are the primary content container throughout the private workspace.

| Variant | Padding | Border-Radius | Shadow | Border |
|---|---|---|---|---|
| **Standard Card** | 16px | 8px (`rounded-lg`) | `shadow-sm` | 1px Slate 200 |
| **Verdict Card (Shield)** | 24px | 12px (`rounded-xl`) | `shadow-md` | 2px status color (Emerald/Crimson/Amber) |
| **Compact Card** (list items) | 12px | 6px (`rounded-md`) | none | 1px Slate 200 |
| **Landing Hero Card** | 32px | 16px (`rounded-2xl`) | `shadow-lg` | none |

**Hover behavior:** Standard and Compact cards gain `shadow-md` on hover with a 150ms ease-out transition. Verdict cards do not change on hover (they are destination, not navigation).

### 10.4 Data Density Profiles

Two density profiles govern the entire app:

**"Gateway" Profile (Public Pages)**
- Line-height: 1.75 (`leading-relaxed`)
- Paragraph max-width: 640px (`max-w-prose`)
- Section gaps: 48–64px
- Font sizes: Headings 36px, body 18px
- Feeling: Breathable, marketing, trustworthy

**"Vault" Profile (Private Workspace)**
- Line-height: 1.5 (`leading-normal`)
- No max-width on content (fills container)
- Section gaps: 16–24px
- Font sizes: Headings 20px, body 14px, data 13px (JetBrains Mono)
- Feeling: Dense, professional, efficient

### 10.5 Grid System

- **Dashboard grids:** CSS Grid with `auto-fill, minmax(320px, 1fr)` for responsive card layouts
- **Data tables:** Full-width within content area, PrimeVue DataTable with sticky headers
- **Form layouts:** Single-column max-width 560px, centered. Two-column for EPR weight entry on desktop (label left, input right)
- **Flight Control:** Fixed 4-column grid on desktop, collapsing to 2-column on tablet, single-column stacked cards on mobile

---

## 11. Detailed Screen Layouts

### 11.1 Landing Page - Public Gateway

The landing page is the product's handshake. It must communicate trust, speed, and simplicity in under 5 seconds.

**Desktop Layout:**

```
+------------------------------------------------------------------+
|  [Logo: risk_guard]                    [Login] [Try Free]        |
+------------------------------------------------------------------+
|                                                                  |
|              Ismerd meg a partnered kockazatat                   |
|              Know your partner's risk in seconds                 |
|                                                                  |
|         +--------------------------------------------+           |
|         | [icon] Adoszam megadasa...  [Ellenorzes]   |           |
|         +--------------------------------------------+           |
|         |  "12345678" or "12345678-1-12" format      |           |
|                                                                  |
+------------------------------------------------------------------+
|  [Shield icon]       [Clock icon]        [Lock icon]             |
|  Deterministic       Less than 30s       Court-Ready             |
|  Verdicts            Results             Evidence                |
+------------------------------------------------------------------+
|  [Social proof / trust badges / partner count]                   |
+------------------------------------------------------------------+
|  Footer: Legal links, disclaimer                                 |
+------------------------------------------------------------------+
```

**Key behaviors:**
- The search input is the singular focal point. No distractions above the fold.
- Input uses intelligent masking: auto-detects 8-digit vs 11-digit format, inserts dashes as the user types.
- Pressing Enter or clicking the CTA triggers a guest search and creates a `guest_sessions` record.
- Below the fold: 3 value proposition cards in a horizontal row, social proof section, footer.
- The entire page uses the "Gateway" density profile with airy spacing and large type.

**Mobile Layout:**
- Same hierarchy but single-column, stacked vertically.
- Search input fills full width with 12px horizontal padding.
- CTA button is full-width below the input, not inline.
- Value proposition cards stack vertically with 24px gaps.

### 11.2 Risk Pulse Dashboard - The Daily Cockpit

This is where authenticated users spend 90% of their time. It must answer one question instantly: "Is anything wrong?"

**Desktop Layout:**

```
+-------+------------------------------------------------------+
|       |  Top Bar: [Breadcrumb]  [Context Switcher] [Avatar]  |
| S     +------------------------------------------------------+
| I     |                                                      |
| D     |  ALERTS BANNER (conditional, shown on status change) |
| E     |  "2 partners changed status since yesterday"  [View] |
| B     +------------------------------------------------------+
| A     |                                                      |
| R     |  [Search Bar: Quick tax number lookup]                |
|       |                                                      |
| ---   |  WATCHLIST                          [+ Add] [Export]  |
| Dash  |  +------------------------------------------------+  |
| Watch |  | Partner Name   | Tax ID      | Status | Age    |  |
| EPR   |  |----------------|-------------|--------|--------|  |
| Admin |  | Kovacs Kft     | 1234-56-78  | [GRN]  | 2m ago |  |
|       |  | Nagy Bt        | 9876-54-32  | [RED]  | 1h ago |  |
|       |  | Szabo Zrt      | 5555-12-34  | [GRY]  | 49h!   |  |
|       |  +------------------------------------------------+  |
|       |                                                      |
+-------+------------------------------------------------------+
```

**Key behaviors:**
- **Alerts Banner:** Appears at the top ONLY when status changes occurred since last login. Uses Amber background for warnings, Crimson for new At-Risk. Clicking "View" scrolls to and highlights the changed partners.
- **Watchlist Table:** PrimeVue DataTable sorted by risk severity by default. At-Risk first, then Stale, then Reliable. Each row shows a compact Shield icon, partner name, tax ID in JetBrains Mono, status badge, and data age.
- **Quick Search:** Always visible above the watchlist. Searching opens the Verdict Detail page.
- **Status badges:** Emerald pill "Megbizhato" for Reliable, Crimson pill "Kockazatos" for At-Risk, Amber pill "Elavult" for Stale, Grey pill "Nem elerheto" for Unavailable. Each pill has its corresponding icon prefix.
- **Row click:** Opens the full Verdict Detail page for that partner.

**Mobile Layout:**
- No sidebar. Bottom tab bar with 4 icons: Dashboard, Watchlist, EPR, Profile.
- Alerts banner is a dismissible card at top.
- Watchlist renders as stacked Compact Cards instead of a table. Each card shows partner name, tax ID, Shield icon with status text, and data age. Tap opens Verdict Detail.

### 11.3 Verdict Detail Page - Shield Card with Provenance

The moment of truth. This page must feel authoritative and complete.

**Desktop Layout:**

```
+-------+------------------------------------------------------+
|       |  Top Bar: [Back to Dashboard]    [Context] [Avatar]  |
| S     +------------------------------------------------------+
| I     |                                                      |
| D     |  +------------------+  +---------------------------+ |
| E     |  |                  |  |  PROVENANCE SIDEBAR       | |
| B     |  |  THE SHIELD CARD |  |                           | |
| A     |  |                  |  |  NAV Adossag              | |
| R     |  |  [SHIELD ICON]   |  |  Utolso lekerdezes: 2p   | |
|       |  |                  |  |  URL: nav.gov.hu/...      | |
|       |  |  MEGBIZHATO      |  |  [checkmark]              | |
|       |  |  Reliable        |  |                           | |
|       |  |                  |  |  e-Cegjegyzek             | |
|       |  |  Tax: 1234-56-78 |  |  Utolso lekerdezes: 5p   | |
|       |  |  SHA: a1b2c3...  |  |  URL: e-cegjegyzek.hu/.. | |
|       |  |                  |  |  [checkmark]              | |
|       |  |  [Export PDF]    |  |                           | |
|       |  |  [Add to Watch]  |  |  Freshness: FRESH        | |
|       |  +------------------+  +---------------------------+ |
|       |                                                      |
|       |  DISCLAIMER                                          |
|       |  "Informational Purpose Only..."                     |
|       |                                                      |
+-------+------------------------------------------------------+
```

**Key behaviors:**
- **Shield Card:** Takes 60% of content width. Large shield icon at 80px centered at top. Status text in both Hungarian and English. Tax number in JetBrains Mono. SHA-256 hash truncated with copy-on-click. Two action buttons: "Export PDF" as Primary and "Add to Watchlist" as Secondary.
- **Provenance Sidebar:** Takes 40% of content width. Lists each data source with its last-scraped timestamp in relative format like "2 minutes ago", the official URL as a tertiary link, and a checkmark or X or clock icon for status.
- **Freshness indicator:** At bottom of provenance sidebar. Shows FRESH in green, AGING in amber when older than 24h, or STALE in grey when older than 48h.
- **Disclaimer:** Below both columns, full-width, in Slate 500 small text.

**Mobile Layout:**
- Single column. Shield Card on top at full width, centered. Provenance collapses into an expandable accordion section below labeled "Adatforrasok reszletei" meaning Data source details. "Export PDF" triggers `navigator.share` via the AuditDispatcher. "Add to Watchlist" is a floating action button at bottom-right.

### 11.4 EPR Material Library and Wizard

**Desktop Layout:**

```
+-------+------------------------------------------------------+
|       |  Top Bar: [EPR then Material Library]  [Ctx] [Avatar]|
| S     +------------------------------------------------------+
| I     |                                                      |
| D     |  MY MATERIALS              [+ New Material] [Copy Q] |
| E     |  +------------------------------------------------+  |
| B     |  | Name             | KF-Code  | Weight | Status |  |
| A     |  |------------------|----------|--------|--------|  |
| R     |  | Kartondoboz A    | 99 01 01 | 0.45kg | Verifd |  |
|       |  | Muanyag palack B | --       | 0.12kg | Draft  |  |
|       |  +------------------------------------------------+  |
|       |                                                      |
|       |  WIZARD when activated for a material:               |
|       |  +------------------------------------------------+  |
|       |  |  Step 1/3: Material Type                       |  |
|       |  |  [Csomagolas] [Elektromos] [Egyeb]             |  |
|       |  |                                                |  |
|       |  |  [Back]                          [Next Step]   |  |
|       |  +------------------------------------------------+  |
|       |                                                      |
+-------+------------------------------------------------------+
```

**Key behaviors:**
- **Material Library Table:** PrimeVue DataTable showing all templates. "Verified" items have Emerald badge with linked KF-code. "Draft" items have Slate badge. Row click opens the material detail.
- **New Material button:** Opens an inline form below the table header, not a modal, for entering material name and base weight.
- **Copy from Previous Quarter button:** Shows a confirmation dialog listing last quarter's materials. One-click duplicates all.
- **Wizard Stepper:** PrimeVue Stepper component. Appears below or replaces the table when activated for a specific material. 3 steps: Material Type, Usage Context, Subtype. Each step shows large tappable option cards instead of small radio buttons. Selecting an option immediately validates via the DAG engine and advances.
- **Result:** After final step, shows the KF-code result in a green confirmation card with a breadcrumb trail of the path taken. "Confirm and Link" button saves the KF-code to the template.

### 11.5 Flight Control - Accountant Aggregate View

**Desktop Layout:**

```
+-------+------------------------------------------------------+
|       |  Top Bar: [Flight Control]  [Context Switcher] [Avt] |
| S     +------------------------------------------------------+
| I     |                                                      |
| D     |  PORTFOLIO OVERVIEW                                  |
| E     |  [Total: 42 clients] [At-Risk: 7] [Stale: 3]        |
| B     |                                                      |
| A     |  +------------------------------------------------+  |
| R     |  | Client      | OK  | Risk | Stale | Last Check |  |
|       |  |-------------|-----|------|-------|------------|  |
|       |  | Kovacs Kft  | 12  | 3    | 1     | 2h ago     |  |
|       |  | Nagy Bt     | 8   | 0    | 0     | 4h ago     |  |
|       |  | Szabo Zrt   | 45  | 4    | 2     | 1h ago     |  |
|       |  +------------------------------------------------+  |
|       |                                                      |
|       |  RECENT ALERTS                                       |
|       |  [Crimson] Kovacs: Partner XY became AT_RISK 2h ago  |
|       |  [Amber]   Szabo: Partner AB became STALE 6h ago     |
|       |                                                      |
+-------+------------------------------------------------------+
```

**Key behaviors:**
- **Summary bar:** Three metric pills at the top. Total clients, total At-Risk partners across all clients, total Stale partners. These update in real-time.
- **Client table:** Sortable by any column. Default sort: most At-Risk partners first. Clicking a client row triggers a context switch to that client and navigates to their Risk Pulse Dashboard.
- **Recent Alerts feed:** Below the table. Chronological list of status changes across all clients in the last 7 days. Each alert shows status color icon, client name, partner name, old and new status, and time ago. Clicking navigates to that specific partner's Verdict Detail page with automatic context switch.

### 11.6 Mission Control - Admin Health Dashboard

**Desktop Layout:**

```
+-------+------------------------------------------------------+
|       |  Top Bar: [Admin then Mission Control]  [Avatar]     |
| S     +------------------------------------------------------+
| I     |                                                      |
| D     |  SYSTEM HEALTH BANNER                                |
| E     |  [All Systems Operational] or [1 Adapter Degraded]   |
| B     |                                                      |
| A     |  SCRAPER ADAPTERS                                    |
| R     |  +------------+  +------------+  +------------+     |
|       |  | NAV        |  | e-Ceg      |  | MOHU       |     |
|       |  | [HEALTHY]  |  | [DEGRADED] |  | [HEALTHY]  |     |
|       |  | MTBF: 720h |  | MTBF: 12h  |  | MTBF: 360h |     |
|       |  | Rate: 99%  |  | Rate: 85%  |  | Rate: 98%  |     |
|       |  | Last: 5min |  | Last: 2h ! |  | Last: 15m  |     |
|       |  | [Quarantn] |  | [Quarantn] |  | [Quarantn] |     |
|       |  +------------+  +------------+  +------------+     |
|       |                                                      |
|       |  EPR CONFIG      [Current: v3.2]  [Edit JSON]        |
|       |  AUDIT LOG       [Search...]                         |
|       |                                                      |
+-------+------------------------------------------------------+
```

**Key behaviors:**
- **Health Banner:** Full-width at top. Green if all healthy, Amber if any degraded, Crimson if any circuit-open. ARIA-live region announces changes.
- **Adapter Cards:** Grid of Standard Cards, one per scraper adapter. Each shows adapter name, health status badge, MTBF hours, success rate percentage, last successful scrape timestamp with a warning indicator if older than 1 hour, and a "Quarantine" toggle button.
- **DEGRADED adapters** get an amber left border on their card. **CIRCUIT_OPEN** adapters get a crimson left border.
- **EPR Config and Audit Log:** Quick-access links at the bottom for the JSON editor and GDPR audit search.


---

## 12. Step-by-Step Interaction Flows

### 12.1 Gabor's Midnight Risk Check (SME Owner, Mobile)

Gabor is finalizing a 10M HUF contract at 10:00 PM from his phone.

| Step | Screen | User Action | System Response |
|------|--------|-------------|-----------------|
| 1 | Landing Page | Opens risk_guard on mobile browser | Landing page loads with search input prominently centered |
| 2 | Landing Page | Types "12345678" into search input | Input auto-formats to "1234-56-78", Zod validates format, CTA button activates |
| 3 | Landing Page | Taps "Ellenorzes" (Check) button | Guest session created. Screen transitions to Verdict Detail page with skeleton loading |
| 4 | Verdict Loading | Watches skeleton animation | Skeleton shows source checklist: "[...] NAV Adossag", "[...] Jogi statusz". Each source gets a checkmark as it completes (2-15 seconds) |
| 5 | Verdict Detail | Sees Rose Shield appear with "KOCKAZATOS" (At-Risk) | Shield Card reveals with a brief scale-up animation. Provenance Sidebar shows "NAV: Active debt detected". SHA-256 hash displayed |
| 6 | Verdict Detail | Taps "Export PDF" button | AuditDispatcher triggers navigator.share with branded PDF. Gabor sends to his email |
| 7 | Verdict Detail | Reads disclaimer at bottom | "Informational Purpose Only" text confirms third-party source origins. Gabor has his evidence for requesting prepayment |

**Total time:** Under 30 seconds from input to verdict.

### 12.2 Judit's Quarterly EPR Sprint (Accountant, Desktop)

Judit must file EPR reports for 40 clients. She needs speed and accuracy.

| Step | Screen | User Action | System Response |
|------|--------|-------------|-----------------|
| 1 | Flight Control | Logs in, lands on Flight Control dashboard | Portfolio overview shows 42 clients. EPR filing status column shows "3 pending" |
| 2 | Flight Control | Clicks first client "Kovacs Kft" in the table | Context Switcher activates: brief ContextGuard interstitial appears, then loads Kovacs dashboard |
| 3 | Risk Pulse Dashboard | Clicks "EPR" in sidebar navigation | Navigates to EPR Material Library for Kovacs Kft |
| 4 | EPR Library | Clicks "Copy from Previous Quarter" | Confirmation dialog shows last quarter's 8 materials. Clicks "Copy All" to duplicate |
| 5 | EPR Library | Reviews materials, updates weight for "Kartondoboz A" | Inline edit on the weight column. Real-time MOHU Gate validation shows green checkmark |
| 6 | EPR Library | Clicks "New Filing" button | Filing workflow opens. Pre-fills all Verified templates. Quantity input fields appear next to each material |
| 7 | Filing Workflow | Enters quantities for each material (e.g., 1200 pcs) | FeeCalculator updates the running total in real-time. Summary card shows total weight and total fee |
| 8 | Filing Workflow | Reviews summary, clicks "Export for MOHU" | Indigo toast: "Export generated in Hungarian (required by MOHU portal)". CSV downloads automatically |
| 9 | Filing Workflow | Filing complete for this client | Success toast with Emerald background. Filing logged in epr_exports table |
| 10 | EPR Library | Uses Context Switcher to select next client | ContextGuard interstitial, then EPR Library loads for next client. Repeat from step 4 |

**Total time per client:** Under 2 minutes after initial template setup.

### 12.3 Outage Resilience Flow

A user attempts a search while a government portal is down.

| Step | Screen | User Action | System Response |
|------|--------|-------------|-----------------|
| 1 | Dashboard | Enters tax number in Quick Search | Search initiates, skeleton loading begins |
| 2 | Verdict Loading | Watches skeleton animation | NAV source gets a checkmark. e-Cegjegyzek source shows a spinning indicator, then an X icon with "Idokorlatozott" (Timed out) |
| 3 | Verdict Detail | Sees Grey Shield with "NEM TELJES" (Incomplete) | Shield Card displays grey with clock icon. Provenance Sidebar clearly shows which source failed and when the last successful scrape was |
| 4 | Verdict Detail | Reads Amber banner at top of Shield Card | Banner text: "Partial data - some sources unavailable. Data from [timestamp]." Button: "Ertesites, ha elerheto" (Alert me when available) |
| 5 | Verdict Detail | Clicks "Alert me when available" button | Toast confirms: "You'll be notified when this source comes back online." Notification preference saved. User continues working with available data |

### 12.4 New User First-Run Experience

A new user registers via Google SSO for the first time.

| Step | Screen | User Action | System Response |
|------|--------|-------------|-----------------|
| 1 | Landing Page | Clicks "Login with Google" | Redirected to Google OAuth consent screen |
| 2 | Google OAuth | Grants consent | Redirected back to risk_guard. New User and Tenant records created. JWT issued |
| 3 | Welcome Screen | Sees personalized welcome overlay | Full-screen overlay: "Udvozlunk, [Name]!" with 3 quick-start options as large cards: "Search a partner", "Set up your Watchlist", "Explore EPR compliance" |
| 4 | Welcome Screen | Clicks "Search a partner" | Welcome overlay dismisses. Dashboard loads with empty Watchlist and the Quick Search bar highlighted with a subtle pulse animation |
| 5 | Dashboard | Types their first tax number | Standard search flow begins. After verdict loads, a tooltip appears on the "Add to Watchlist" button: "Add to monitor this partner automatically" |
| 6 | Verdict Detail | Clicks "Add to Watchlist" | Success toast. Tooltip on Watchlist sidebar item: "Your watchlist - we'll check all partners here every 24 hours." Welcome flow complete |

### 12.5 Guest Demo-to-Signup Conversion

A guest user explores the product and hits the demo limit.

| Step | Screen | User Action | System Response |
|------|--------|-------------|-----------------|
| 1 | Landing Page | Searches first company as guest | Normal verdict flow. Subtle counter appears below search: "1 / 10 companies checked" |
| 2 | Landing Page | Searches 2 more companies | Counter updates: "3 / 10 companies checked". After 3rd check, Amber toast: "3 of 3 daily checks used. Sign up for unlimited." |
| 3 | Landing Page | Tries a 4th search | Search input is replaced by a conversion card: "Daily limit reached. Create a free account to continue checking partners today." Two CTAs: "Sign up with Google" and "Sign up with Email" |
| 4 | Landing Page | Returns next day, searches more | Daily check counter resets. Company counter continues: "6 / 10 companies checked" |
| 5 | Landing Page | Eventually hits 10 company limit | Permanent conversion card: "You've explored 10 companies. Sign up to unlock unlimited monitoring, watchlists, and EPR compliance." Full feature comparison table below |

---

## 13. Empty States and First-Run Experience

Empty states are critical trust-building moments. Each one must feel intentional, not broken.

### 13.1 Design Principles for Empty States

- **Never show a blank page.** Every empty state has an illustration concept, a message, and a clear CTA.
- **Use encouraging language.** Not "No data found" but "Your watchlist is waiting for its first partner."
- **Match the gravity.** Empty watchlist is encouraging. No search results is informative. Error states are transparent.

### 13.2 Empty State Definitions

#### Empty Watchlist (Dashboard, first visit)

- **Illustration concept:** A shield icon with a dotted outline (unfilled), suggesting "ready to protect"
- **Headline:** "A figyelolistad meg ures" (Your watchlist is empty)
- **Body:** "Add partners to automatically monitor their risk status every 24 hours. You'll be the first to know if anything changes."
- **CTA:** Primary button "Elso partner hozzaadasa" (Add your first partner) which focuses the Quick Search input
- **Secondary CTA:** Tertiary link "Learn how watchlists work"

#### No Search Results (Verdict page, invalid or unknown tax number)

- **Illustration concept:** A magnifying glass with a question mark
- **Headline:** "Nem talalhato ceg" (Company not found)
- **Body:** "We couldn't find a company with tax number [entered number]. Please check the format (8 or 11 digits) and try again."
- **CTA:** Primary button "Ujra kereses" (Search again) which clears and focuses the search input
- **Note:** This is distinct from an outage state. Outage shows Grey Shield with provenance details.

#### Empty EPR Library (EPR section, no templates)

- **Illustration concept:** A clipboard with a plus sign
- **Headline:** "Nincs meg anyag sablon" (No material templates yet)
- **Body:** "Create your packaging materials library to streamline your quarterly MOHU filings. Start with your most common packaging type."
- **CTA:** Primary button "Elso anyag letrehozasa" (Create your first material)
- **Secondary CTA:** Tertiary link "Import from previous quarter" if any prior data exists

#### Accountant with No Clients (Flight Control, new accountant)

- **Illustration concept:** A radar dish scanning an empty sky
- **Headline:** "Meg nincsenek ugyfelek" (No clients assigned yet)
- **Body:** "Once your clients grant you access through their tenant settings, they'll appear here automatically. You'll see their entire partner portfolio at a glance."
- **CTA:** Secondary button "Meghivo kuldese" (Send invitation link) to generate a shareable onboarding link
- **Note:** This state should feel patient and professional, not urgent.

#### Guest Rate Limit Reached (Landing page, daily limit)

- **Illustration concept:** A clock with a refresh arrow
- **Headline:** "Napi limit elerve" (Daily limit reached)
- **Body:** "You've used your 3 free checks for today. Create an account to get unlimited checks, automatic monitoring, and court-ready evidence."
- **CTA:** Primary button "Fiok letrehozasa" (Create account) leading to registration
- **Secondary CTA:** Tertiary text "Your limit resets tomorrow at midnight"

#### Guest Company Limit Reached (Landing page, permanent limit)

- **Illustration concept:** A shield with an upward arrow (upgrade)
- **Headline:** "Demo limit elerve" (Demo limit reached)
- **Body:** "You've explored 10 companies with your free access. Sign up to unlock unlimited partner monitoring, automated alerts, and EPR compliance tools."
- **CTA:** Primary button "Regisztracio" (Register) with a feature comparison table below showing Free vs. Paid capabilities

### 13.3 First-Run Welcome Overlay

Shown exactly once, immediately after first SSO or email registration.

**Structure:**
- Full-screen semi-transparent overlay on top of the dashboard
- White card centered, max-width 640px, rounded-2xl, shadow-xl
- Close button (X) in top-right corner to skip

**Content:**
```
Udvozlunk a risk_guard-ban, [First Name]!

Three things you can do right now:

[Shield Icon Card]          [Eye Icon Card]         [Clipboard Icon Card]
Search a partner            Set up monitoring        Start EPR compliance
Enter a tax number          Build your watchlist     Create material templates
to get an instant           for automatic 24h        for effortless quarterly
risk verdict.               status checks.           MOHU filings.

                    [Start Exploring]  (dismisses overlay)
```

- The three cards are clickable and navigate to the relevant feature
- "Start Exploring" dismisses the overlay and is never shown again
- Clicking X also dismisses permanently
- Preference stored in user's localStorage and user profile

---

## 14. Micro-Interactions and Motion Design

Motion in risk_guard serves two purposes: communicating state changes and building trust. Animations should feel precise and confident, never playful or bouncy. Think "bank vault door" not "party balloon."

### 14.1 Global Timing Tokens

| Token | Duration | Easing | Usage |
|-------|----------|--------|-------|
| `instant` | 0ms | none | Immediate state changes like checkbox toggles |
| `quick` | 150ms | ease-out | Hover effects, button feedback, tooltip appearance |
| `smooth` | 300ms | ease-in-out | Card transitions, accordion expand/collapse, sidebar toggle |
| `deliberate` | 500ms | ease-in-out | Shield reveal, page transitions, overlay appear/dismiss |
| `patient` | 1000ms | ease-in | Skeleton shimmer cycle, progress bar segments |

**Rule:** If you have to think about whether an animation is too slow, it is. Default to `quick` and only use `deliberate` for high-impact moments.

### 14.2 Shield Verdict Reveal

This is the signature moment of the product. It must feel definitive.

**Sequence:**
1. Skeleton loading state: Shield Card area shows a grey rounded rectangle pulsing with `patient` shimmer. Provenance Sidebar shows source checklist with animated dots.
2. Sources complete one by one: Each source row transitions from "[...] Source Name" to "[checkmark] Source Name" with a `quick` fade. This creates a sense of real-time progress.
3. All sources complete: Brief 200ms pause to build anticipation.
4. Shield reveal: The grey skeleton fades out and the colored Shield Card fades in with a subtle `deliberate` scale from 0.95 to 1.0. The status color border appears simultaneously.
5. Hash appears: The SHA-256 hash types out character-by-character over 500ms in JetBrains Mono. This is the only "decorative" animation and it reinforces the cryptographic integrity feel.

**Never skip this sequence.** Even if results are cached and instant, play steps 3-5 at minimum. The reveal builds trust.

### 14.3 Page Transitions

- **Between main sections** (Dashboard to EPR to Admin): Instant content swap with no transition. The sidebar active state updates with a `quick` border color change. Reason: Accountants switching rapidly between sections should feel zero friction.
- **Drill-down navigation** (Dashboard to Verdict Detail, or Flight Control to Client Dashboard): Content area fades to Slate 50 over `quick` 150ms, then new content fades in over `quick` 150ms. Breadcrumb updates simultaneously.
- **Context Switch** (Accountant switching tenants): Full ContextGuard interstitial appears with `deliberate` fade-in. Shows tenant name, loading spinner, and a "Switching to [Client Name]..." message. Content loads behind it, then interstitial fades out with `smooth` timing.
- **Mobile bottom sheet** (Provenance details on mobile): Slides up from bottom with `smooth` 300ms and a subtle spring feel. Dragging down dismisses with velocity-based animation.

### 14.4 Toast Notification Behavior

| Property | Value |
|----------|-------|
| **Position** | Top-right on desktop, top-center on mobile |
| **Appear animation** | Slide in from right (desktop) or drop down (mobile), `smooth` 300ms |
| **Auto-dismiss** | Success: 4 seconds. Warning: 6 seconds. Error: Manual dismiss only |
| **Stacking** | Maximum 3 visible. Oldest dismissed when 4th arrives |
| **Click behavior** | Clicking a toast dismisses it. If it contains an action link, the link fires first |
| **Width** | 360px max on desktop, full-width minus 24px on mobile |

**Toast variants map to feedback patterns:**
- **Success (Emerald left border):** "Partner added to watchlist", "Export generated", "Filing saved"
- **Warning (Amber left border):** "Export generated in Hungarian", "Data is 36 hours old", "1 source unavailable"
- **Error (Crimson left border):** "Search failed - please try again", "Session expired - please log in", "Export generation failed"
- **Info (Indigo left border):** "New feature available", "System maintenance scheduled"

### 14.5 Context Switcher Interaction Model

The Context Switcher is a PrimeVue AutoComplete dropdown in the Top Bar, visible only to users with the ACCOUNTANT role.

**Interaction sequence:**
1. **Resting state:** Shows current tenant name in a pill badge with a subtle down-arrow icon. Pill has a Slate 200 border.
2. **Click/Focus:** Dropdown expands with `smooth` animation. Search input auto-focused. Recent clients shown immediately (max 5).
3. **Typing:** Results filter in real-time as the user types. Matching text is bold-highlighted in results. Debounced at 200ms.
4. **Selection:** User clicks or presses Enter on a client name. Dropdown closes with `quick` animation.
5. **Transition:** ContextGuard interstitial appears (see 14.3). New JWT issued with updated active_tenant_id. Dashboard reloads with new client context.
6. **Error:** If token refresh fails, ContextGuard shows "Session expired. Please log in again." with a login button.

### 14.6 Button Feedback States

| State | Primary Button | Secondary Button | Tertiary Button |
|-------|---------------|-----------------|-----------------|
| **Default** | Deep Navy bg, white text | White bg, Slate 600 border, Slate 700 text | No bg/border, Indigo 600 text |
| **Hover** | Slate 800 bg (slightly lighter) | Slate 50 bg | Indigo 700 text, underline |
| **Active/Pressed** | Slate 900 bg, scale 0.98 | Slate 100 bg, scale 0.98 | Indigo 800 text |
| **Disabled** | Slate 300 bg, Slate 500 text | Slate 100 bg, Slate 400 text/border | Slate 400 text |
| **Loading** | Original bg + inline spinner replacing text | Original + inline spinner | Text replaced with "..." pulse |

**Transition timing:** All hover and active state changes use `quick` 150ms ease-out.

### 14.7 Skeleton Loading Patterns

Skeletons must mirror the exact layout of the content they replace. This means:

- **Watchlist table skeleton:** Shows 5 rows of grey bars matching column widths. Header is real (not skeleton). Shimmer animation runs left-to-right at `patient` speed.
- **Shield Card skeleton:** Grey rounded rectangle matching Shield Card dimensions. No icon. Single shimmer animation.
- **Provenance Sidebar skeleton:** 3 rows of grey bars with different widths (mimicking source name lengths). Each has a grey circle on the left (mimicking status icon).
- **Dashboard metric pills:** 3 grey rounded pills at the top where summary counts will appear.

**Rule:** Skeletons never animate vertically (no bounce or grow). Only horizontal shimmer. This feels stable and professional.

---

## 15. Notification and Communication Design

### 15.1 Communication Hierarchy

risk_guard communicates with users through 4 channels, ordered by urgency:

| Channel | Urgency | When | Example |
|---------|---------|------|---------|
| **In-App Toast** | Immediate, transient | Action feedback, minor warnings | "Partner added to watchlist" |
| **In-App Alert Banner** | Session-persistent | Status changes since last login | "2 partners changed status" |
| **Email Notification** | Asynchronous, important | Partner status changes, system alerts | "Your partner Kovacs Kft is now At-Risk" |
| **Dashboard Badge** | Passive, persistent | Unread alerts count | Red badge (3) on Watchlist sidebar icon |

### 15.2 In-App Alert Banner Behavior

- Appears at the top of the Dashboard content area, below the Top Bar
- Background: Amber 50 for warnings, Crimson 50 for critical changes
- Left border: 4px in the corresponding status color
- Content: "[count] partner(s) changed status since your last visit" with a [View Changes] link
- Clicking [View Changes] scrolls to the Watchlist and temporarily highlights changed rows with a subtle Amber 50 background that fades after 3 seconds
- Dismissible via X button. Does not reappear once dismissed for that login session
- If the user has not logged in for more than 7 days, banner includes a "Weekly Summary" link to a digest view

### 15.3 Email Alert Template Structure

All emails follow a consistent template using the "Safe Harbor" visual identity:

**Header:**
- risk_guard logo (dark version) on white background
- Horizontal rule in Slate 200

**Body:**
```
Kedves [User Name],

[STATUS ICON] [Partner Name] statusza megvaltozott

Korabbi: MEGBIZHATO (Reliable)
Jelenlegi: KOCKAZATOS (At-Risk)

Reszletek:
- Adoszam: [Tax Number in JetBrains Mono style]
- Ellenorizve: [Timestamp]
- SHA-256: [Hash truncated]

[Primary CTA Button: "Reszletek megtekintese" (View Details)]

---
Disclaimer: "Informational Purpose Only..."
```

**Footer:**
- "This email was sent because [Partner Name] is on your watchlist."
- Unsubscribe link for individual partner or all notifications
- risk_guard logo (small), legal links

**Localization:** Email language follows the user's `preferred_language` setting. Hungarian is default.

### 15.4 Destructive Action Confirmations

Any action that deletes data or changes system state significantly requires explicit confirmation:

| Action | Confirmation Type | Message |
|--------|------------------|---------|
| Remove partner from Watchlist | Inline confirm (button changes to "Are you sure?" for 3 seconds) | "Remove [Partner Name]? You'll stop monitoring this partner." |
| Delete material template | Modal dialog | "Delete [Template Name]? This cannot be undone. Any linked KF-codes will be unlinked." |
| Quarantine scraper adapter | Modal dialog with text input | "Type 'QUARANTINE' to confirm. This will mark all verdicts from [Adapter] as Unavailable." |
| Publish new EPR config | Modal dialog with validation summary | "Publish EPR Config v[N]? [X/Y] golden test cases passed. This will affect all new calculations." |
| Context switch (accountant) | ContextGuard interstitial, automatic (no user input needed) | "Switching to [Client Name]..." with a brief loading state |

**Design rules for confirmations:**
- Never use generic "Are you sure?" messages. Always state the specific consequence.
- Destructive CTAs use Crimson background. Cancel uses Secondary (grey border) style.
- Modal dialogs use a semi-transparent Slate 900 backdrop with the dialog card centered.
- Inline confirms auto-revert to the original button after 3 seconds if not clicked.

### 15.5 Status Change Communication Matrix

When a partner's status changes, the system communicates through multiple channels simultaneously:

| Event | Toast | Alert Banner | Email | Dashboard Badge |
|-------|-------|-------------|-------|----------------|
| RELIABLE to AT_RISK | N/A (background) | Yes, Crimson | Yes, immediate | Yes, increment |
| AT_RISK to RELIABLE | N/A (background) | Yes, Emerald | Yes, immediate | Yes, increment |
| Any to STALE (48h) | N/A (background) | Yes, Amber | No (non-urgent) | Yes, increment |
| Any to UNAVAILABLE | N/A (background) | Yes, Grey | Only if persists more than 24h | Yes, increment |
| Scraper DEGRADED | Admin toast | Admin banner | Admin email | Admin badge |
| EPR Config published | Admin toast | No | No | No |

**Rule:** Background monitoring events never trigger toasts because the user is not actively waiting for them. Toasts are reserved for direct user actions.
