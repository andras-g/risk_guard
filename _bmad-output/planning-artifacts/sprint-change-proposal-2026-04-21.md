# Sprint Change Proposal — CP-6: EPR Filing Navigation & Discoverability Gap

**Date:** 2026-04-21
**Trigger:** Demo accountant user session discovered `/epr/filing` has zero UI entry points; users cannot reach the quarterly filing page except via direct URL.
**Proposed by:** Andras (PO) + Bob (SM)
**Scope classification:** **Moderate** — new Story 10.10, frontend-only, no DB/backend changes.
**Mode:** Batch review (single proposal)

---

## 1. Issue Summary

After Epic 10's product-first filing flow went live (Stories 10.1–10.9), a demo accountant session revealed that the `/epr/filing` page has **no discoverable UI path** from anywhere in the application. Users have to type the URL manually or follow bookmarks to reach the core value-delivery screen of the PRO_EPR tier.

**Evidence — confirmed by systematic audit (placeholder file paths + line refs):**

| Entry point | State | File |
|---|---|---|
| Desktop sidebar | No `eprFiling` nav item | `frontend/app/components/Common/AppSidebar.vue:143-155` |
| Mobile drawer | No `eprFiling` nav item | `frontend/app/components/Common/AppMobileDrawer.vue` |
| Top bar | Breadcrumb only, no CTA | `frontend/app/components/Common/AppTopBar.vue` |
| `common.nav.*` i18n namespace | No `eprFiling` key (only `dashboard, registry, screening, watchlist, admin`) | `frontend/app/i18n/hu/common.json:52-58` |
| Registry page header | Only "Új termék" button; no filing CTA | `frontend/app/pages/registry/index.vue:157-164` |
| InvoiceBootstrapDialog completion summary | Only "Megnyitás" → `/registry?reviewState=MISSING_PACKAGING`; no filing CTA | `frontend/app/components/Registry/InvoiceBootstrapDialog.vue:369-376` |
| RegistryOnboardingBlock | Secondary CTA → `/registry/new`; no filing CTA | `frontend/app/components/registry/RegistryOnboardingBlock.vue` |
| Flight Control (accountant home) | No EPR filing widget | `frontend/app/pages/flight-control/*.vue` |
| Dashboard (SME_ADMIN home) | No EPR filing widget | `frontend/app/pages/dashboard/*.vue` |
| Producer profile page | No filing exit link after setup | `frontend/app/pages/settings/producer-profile.vue` |
| E2E tests | All three filing-related specs use `page.goto('/epr/filing')` — direct URL, never via UI click | `filing-workflow.e2e.ts`, `submission-history.e2e.ts`, `empty-registry-onboarding.e2e.ts` |

**Root cause — traceable to Story 10.1:**
Story 10.1 explicitly scoped "Menu changes: `AppSidebar.vue` removes the `Anyagkönyvtár` link for all roles. `/epr` route is deleted (no redirect — confirmed no production users)." The story added an ArchUnit-verified deletion for the Anyagkönyvtár link but **did not add a replacement menu entry for the filing page**. `AppSidebar.spec.ts` passed because the assertion only checked the absence of Anyagkönyvtár, not the presence of any filing entry. The gap is a scope omission, not a regression.

**Impact of the gap:**
- **User onboarding friction:** A new PRO_EPR tenant who completes bootstrap and Registry setup has no obvious next step; the `/registry` page does not signal "now generate your filing here".
- **Accountant portfolio workflow broken:** An accountant switching between client tenants has no single-click path to each tenant's filing — breaks the "Flight Control" mental model.
- **Discoverability debt for submission history:** Story 10.9's submission-history panel lives at the bottom of `/epr/filing`. A user wanting to re-download an OKIRkapu XML has no way to find it without prior product knowledge.
- **E2E tests mask the gap:** All E2E tests bypass the UI path (`page.goto`), so this defect cannot be caught by the existing test surface.

**Confirmation in `deferred-work.md:88` (Story 8.5, 2026-04-09):** the note `AppSidebar shows PLATFORM_ADMIN SME-oriented nav items (Watchlist, Screening, EPR Filing)` indicates a sidebar entry for "EPR Filing" may have previously existed and was inadvertently removed during Story 10.1's Anyagkönyvtár deletion. The deferred note is now **stale** (no such entry exists today) and should be cleaned up as part of this change.

---

## 2. Impact Analysis

### Epic Impact

**Epic 10 (in-progress):** Story 10.7 is the only in-flight story; it addresses the **empty-registry** onboarding block inside `/registry` and `/epr/filing`. It does not add a sidebar entry and cannot be stretched to cover this without scope creep. **Recommendation: add a new Story 10.10 — do NOT extend 10.7.**

**Completed Epic 10 stories (10.1–10.6, 10.8, 10.9):** No rollback. Each story was correct within its scoped ACs; the gap is in scope *between* stories.

**Epics 1–9:** Unaffected.

### Story Impact

| Story | Change | Why |
|---|---|---|
| **(new) 10.10** | **EPR Filing Navigation & Discoverability** — sidebar entry, mobile drawer entry, registry page CTA, InvoiceBootstrapDialog completion CTA, breadcrumb cleanup, i18n keys, tests | Closes the scope gap created by Story 10.1's menu deletion |
| 10.7 (in-progress) | No change. Remains focused on empty-registry onboarding. | Keep scope tight; 10.7 already has 31 ACs and 15 tasks |
| 10.1 (done) | No retroactive change to the story file. Note the gap in Epic 10 retrospective. | Historical accuracy |
| 8.5 deferred note D6 | Mark as **resolved (stale)** — the claim no longer reflects reality | `deferred-work.md:88` cleanup |

### Artifact Conflicts

| Artifact | Impact | Action Needed |
|---|---|---|
| `AppSidebar.vue` + `AppSidebar.spec.ts` | Missing `eprFiling` nav item; spec has no assertion for its presence | Add nav item; add spec test |
| `AppMobileDrawer.vue` + spec | Same gap on mobile | Add nav item; add spec test |
| `frontend/app/i18n/hu/common.json` + `en/common.json` | Missing `common.nav.eprFiling` key | Add keys (alphabetical per T6 hook) |
| `registry/index.vue` | Header has only "Új termék" — no filing CTA when Registry is populated | Add secondary "Negyedéves bejelentés" button |
| `components/Registry/InvoiceBootstrapDialog.vue` | Completion summary only routes to Registry filter | Add secondary CTA to `/epr/filing` when bootstrap completes successfully |
| `AppBreadcrumb.vue` | `OBSOLETE_PARENT_SEGMENTS` still contains `'epr'`; breadcrumb for `/epr/filing` shows "EPR" as non-clickable | Remove `'epr'` from obsolete set once nav entry exists, OR keep behaviour and document (since no `/epr` index exists) |
| `E2E tests` | No spec asserts the sidebar→filing click path works | New E2E `nav-to-filing.e2e.ts` |
| `epics.md` | Epic 10 section lists Stories 10.1–10.9 | Add Story 10.10 heading |
| `sprint-status.yaml` | Epic 10 entry lists through 10.9 | Add `10-10-epr-filing-navigation-and-discoverability: backlog` |
| `deferred-work.md:88` | Stale D6 claim | Mark resolved |

### Technical Impact

- **DB migrations:** None.
- **Backend:** None.
- **Frontend-only change:**
  - `AppSidebar.vue` — 1 line added to `mainNavItems` array (PRO_EPR-gated, same pattern as `registry`).
  - `AppMobileDrawer.vue` — same pattern.
  - `registry/index.vue` — 1 secondary button in header.
  - `InvoiceBootstrapDialog.vue` — 1 secondary CTA in completion footer.
  - i18n keys — 2 new keys (`common.nav.eprFiling` in hu + en), alphabetical.
  - Tests — ~6 new vitest cases + 1 new Playwright spec.
- **Dev effort estimate:** ~4 hours dev + 1 round code review. (Single story, Low effort, Low risk.)
- **Risk:** Very low. No backend contract change. Existing `useTierGate` + role checks remain the source of truth.

---

## 3. Recommended Approach

**Option 1 — Direct Adjustment via new Story 10.10 — RECOMMENDED**

**Rationale:**
- The fix is additive (new nav entry + CTAs), not a redesign. Fits within Epic 10's existing architecture.
- Story 10.7 is in-progress with a clean, focused scope; extending it would delay its close and violate the retrospective T1 rule ("AC-to-task walkthrough before code").
- A dedicated Story 10.10 gives the navigation concern its own code review lens and its own E2E spec — easier to verify completeness.
- No production users are affected by the gap during the fix window (demo tenants only); zero-downtime rollout.

**Options considered and rejected:**

- **Option 2 — Extend Story 10.7 with nav scope.** Rejected: 10.7 is already 31 ACs / 15 tasks; adding 8+ ACs breaks the retro T1 rule.
- **Option 3 — Hotfix directly to `main` without a story.** Rejected: violates the "named story task for every action item" rule from Epic 5 retrospective (P1).
- **Option 4 — Defer to Epic 11 (UX polish).** Rejected: this is a blocker for accountant acceptance testing (explicitly deferred post-10.9 per `epics.md:896`); cannot happen with no filing entry point.

**Effort estimate:** Low (~4 hours dev + 1 code review round).
**Risk:** Low. Frontend-only; existing tier/role machinery reused.
**Timeline impact:** Zero on Epic 10 close — Story 10.10 can run parallel to 10.7 completion and Epic 10 retrospective.

---

## 4. Detailed Change Proposals

### 4.1 New Story 10.10 — EPR Filing Navigation & Discoverability

**Status:** backlog (to be created via `bmad-create-story`)
**Dependencies:** Stories 10.1, 10.6, 10.9 (all done); parallel-safe with 10.7.
**Scope:** Frontend-only.

**Story:**
> As an **SME_ADMIN, ACCOUNTANT, or PLATFORM_ADMIN user**,
> I want a **persistent navigation entry for "Negyedéves bejelentés" in the sidebar and mobile drawer, plus contextual CTAs on the Registry page and after invoice bootstrap completes**,
> so that **I can reach the EPR filing page in one click from anywhere in the application, instead of typing the URL**.

**Acceptance Criteria (draft — refined during `bmad-create-story`):**

1. **`AppSidebar.vue` desktop nav** adds `{ key: 'eprFiling', to: '/epr/filing', icon: 'pi-file' }` to `mainNavItems`, positioned **directly after `registry`**. PRO_EPR tier-gated (same `hasProEpr` computed as `registry`). Role-visible to SME_ADMIN, ACCOUNTANT, PLATFORM_ADMIN (default — no `accountantOnly` flag).

2. **`AppMobileDrawer.vue`** mirrors the same entry with identical gating and order.

3. **i18n keys** added alphabetically to `frontend/app/i18n/hu/common.json` and `en/common.json` under `common.nav`:
   - `eprFiling: "Negyedéves bejelentés"` (HU)
   - `eprFiling: "Quarterly filing"` (EN)
   Run `npm run -w frontend lint:i18n` → 22/22 clean.

4. **`registry/index.vue` header** gets a secondary outlined button `Negyedéves bejelentés` (icon `pi-file`, severity `secondary`, `outlined`) next to the existing "Új termék" primary button. Visible only when `registryCompleteness.productsWithComponents.value > 0` (no filing to generate without components). Click routes to `/epr/filing`. `data-testid="header-cta-filing"`.

5. **`InvoiceBootstrapDialog.vue` completion footer** adds a secondary CTA `Negyedéves bejelentés megnyitása` next to the existing "Megnyitás" button, visible only on `status === 'COMPLETED' && createdProducts > 0 && unresolvedPairs === 0`. Routes to `/epr/filing`. `data-testid="bootstrap-cta-filing"`.

6. **`RegistryOnboardingBlock.vue` context='registry'** — no change (still routes to bootstrap / `/registry/new`). The filing CTA on the Registry page (AC #4) covers the populated-registry case.

7. **Breadcrumb behaviour** — `AppBreadcrumb.vue` keeps the current non-clickable `/epr` parent treatment (since no `/epr` index exists). Update the code comment to reflect the new state: Story 10.1 removed `/epr` (Anyagkönyvtár); `/epr/filing` is the **only** `/epr/*` route and is now reachable via the new sidebar entry.

8. **Deferred-work note D6 cleanup** — mark `deferred-work.md:88` as resolved (the claim is stale).

9. **`AppSidebar.spec.ts`** extends with 3 new tests:
   - `eprFiling` nav item visible for PRO_EPR-tiered SME_ADMIN.
   - `eprFiling` nav item visible for PRO_EPR-tiered ACCOUNTANT.
   - `eprFiling` nav item **hidden** for ALAP-tier user (tier gate works).

10. **`AppMobileDrawer.spec.ts`** mirrors the same 3 tests.

11. **`registry/index.spec.ts`** adds 2 tests:
    - Filing CTA visible when `productsWithComponents > 0`.
    - Filing CTA hidden when `productsWithComponents === 0` (onboarding block covers that state).

12. **`InvoiceBootstrapDialog.spec.ts`** adds 2 tests:
    - Filing CTA visible on COMPLETED with `createdProducts > 0 && unresolvedPairs === 0`.
    - Filing CTA hidden on FAILED/FAILED_PARTIAL/CANCELLED.

13. **New E2E `frontend/e2e/nav-to-filing.e2e.ts`**:
    - Sign in as demo accountant → click sidebar "Negyedéves bejelentés" → assert URL is `/epr/filing` → assert page heading visible.
    - Sign in as demo SME_ADMIN → same path.
    - Sign in as demo ALAP-tier user → assert sidebar item is NOT present.

14. **AC-to-task walkthrough (retro T1)** filed in Dev Agent Record before any code is committed.

**Tasks / Subtasks (draft):**
- Task 1 — AC-to-task walkthrough gate (T1).
- Task 2 — i18n keys (hu + en) + `lint:i18n` verification.
- Task 3 — `AppSidebar.vue` + spec (AC #1, #9).
- Task 4 — `AppMobileDrawer.vue` + spec (AC #2, #10).
- Task 5 — `registry/index.vue` CTA + spec (AC #4, #11).
- Task 6 — `InvoiceBootstrapDialog.vue` CTA + spec (AC #5, #12).
- Task 7 — Breadcrumb comment update (AC #7).
- Task 8 — E2E `nav-to-filing.e2e.ts` (AC #13).
- Task 9 — `deferred-work.md` D6 cleanup (AC #8).
- Task 10 — Full verification: vitest, tsc, ESLint, lint:i18n, Playwright.

---

### 4.2 Update `epics.md`

**Location:** `_bmad-output/planning-artifacts/epics.md`, end of Epic 10 section (after Story 10.9).

**Insert:**

```markdown
### Story 10.10: EPR Filing Navigation & Discoverability
**Goal:** Close the UX discoverability gap left by Story 10.1's `/epr` menu deletion. Add a persistent sidebar nav entry for `/epr/filing` (PRO_EPR-gated), mirror it in the mobile drawer, surface a secondary CTA on the Registry page header when the Registry has components, and add a "Negyedéves bejelentés megnyitása" CTA to the InvoiceBootstrapDialog completion summary.

**Dependencies:** Stories 10.1, 10.6, 10.9 (all done); parallel-safe with 10.7.

**Architecture:**
- Frontend-only.
- New sidebar/drawer nav entry re-uses the existing `useTierGate('PRO_EPR')` + role-visibility machinery from `registry`.
- New CTAs on `registry/index.vue` and `InvoiceBootstrapDialog.vue` use existing router + i18n patterns.
- No new API endpoints; no DB changes; no ArchUnit rule changes.

**Acceptance Criteria:**
- Sidebar and mobile drawer both expose a "Negyedéves bejelentés" nav entry for PRO_EPR-tiered SME_ADMIN, ACCOUNTANT, PLATFORM_ADMIN users; hidden for ALAP/PRO tiers.
- Registry page header shows a secondary "Negyedéves bejelentés" button when at least one product has packaging components.
- InvoiceBootstrapDialog completion footer shows a secondary "Bejelentés megnyitása" CTA on successful COMPLETED status with created products and zero unresolved pairs.
- i18n keys added alphabetically to hu + en `common.json`.
- Existing E2E tests continue to pass; new `nav-to-filing.e2e.ts` covers the three sign-in flows (accountant, SME_ADMIN, ALAP-tier exclusion).
- AC-to-task walkthrough (retro T1) filed in Dev Agent Record before any code is committed.

**Non-goals:**
- No redesign of the sidebar IA.
- No new dashboard widget for EPR filing (deferred to potential Epic 11 polish).
- No in-app tutorial overlay (deferred).
```

---

### 4.3 Update `sprint-status.yaml`

**Insert in `development_status` block within `epic-10` section** (after the `10-9-submission-history` line):

```yaml
  10-10-epr-filing-navigation-and-discoverability: backlog  # Nav gap surfaced 2026-04-21 by demo accountant session; see sprint-change-proposal-2026-04-21.md (CP-6). Frontend-only; adds sidebar + mobile drawer entry, registry page CTA, InvoiceBootstrapDialog completion CTA, E2E nav test. Parallel-safe with 10.7. Low effort / low risk.
```

**Update `last_updated` line** to reflect 2026-04-21 CP-6 creation.

---

### 4.4 Update `deferred-work.md` (D6 cleanup)

**Location:** `_bmad-output/implementation-artifacts/deferred-work.md:88`

**OLD:**
```
- D6: `AppSidebar` shows PLATFORM_ADMIN SME-oriented nav items (Watchlist, Screening, EPR Filing) — these features are irrelevant to a platform operator; pre-existing nav structure; address in UX polish sprint.
```

**NEW:**
```
- D6: ~~`AppSidebar` shows PLATFORM_ADMIN SME-oriented nav items (Watchlist, Screening, EPR Filing)~~ **RESOLVED STALE (2026-04-21, CP-6):** The "EPR Filing" entry no longer exists in the sidebar (removed inadvertently by Story 10.1's Anyagkönyvtár deletion). Story 10.10 re-adds it with proper tier gating. Watchlist + Screening irrelevance for PLATFORM_ADMIN remains valid but is deferred to Epic 11 UX polish.
```

---

## 5. Implementation Handoff

**Scope classification:** **Moderate**.

**Handoff recipients:**

1. **Scrum Master (Bob)** — create Story 10.10 file via `bmad-create-story`. Use the AC draft from §4.1 as the starting point; refine during the create-story workflow.

2. **Developer Agent (Amelia)** — implement Story 10.10 via `bmad-dev-story` once the story file is ready-for-dev. Follow the Task 1 AC-to-task walkthrough gate (retro T1).

3. **Code Reviewer** — single review round expected (Blind Hunter + Edge Case Hunter + Acceptance Auditor in parallel, per Story 10.x pattern). Low complexity → 1 round likely sufficient.

4. **Andras (PO)** — final manual acceptance test: sign in as demo accountant → confirm sidebar entry → click → land on `/epr/filing` → confirm filing flow end-to-end.

**Success criteria:**

- Sidebar shows "Negyedéves bejelentés" for demo accountant within 1 click of login.
- Registry page header shows filing CTA once Registry has components.
- Bootstrap completion summary offers filing CTA on success.
- All existing vitest + Playwright tests green; new `nav-to-filing.e2e.ts` green.
- `lint:i18n` 22/22 green (alphabetical ordering).
- `sprint-status.yaml` updated.
- `deferred-work.md` D6 marked resolved.

**Next steps after Story 10.10 close:**

- Epic 10 retrospective (currently blocked on 10.7 + 10.10).
- Retrospective should surface the scope-gap pattern (menu deletion without replacement) as a recurring risk; consider adding a "navigation completeness" checklist to the epic-retrospective skill.

---

**Approval requested from:** Andras (PO).
**Approval options:** `yes` / `no` / `revise <section>`
