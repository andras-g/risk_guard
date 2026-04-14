# Sprint Change Proposal — CP-5: Product-Packaging Registry & Invoice-Driven EPR Calculation

**Date:** 2026-04-14
**Trigger:** EPR packaging calculation gap identified during domain review (see `epr-packaging-calculation-gap-2026-04-14.md`)
**Proposed by:** Andras (product owner) + Bob (SM) + Amelia (Dev)
**Scope:** Major — new epic, new data model, architectural invariants introduced
**Research basis:** Hungarian market scan (MOHU, Körforgó, 80/2023 primary sources) + EU comparables (LUCID/ZSVR, CITEO, PPWR 2025/40). See research section §6.

---

## 1. Issue Summary

Story 8.3 (Invoice-Driven EPR Auto-Fill) shipped with an implicit scope assumption that does not hold for the product's actual target ICP.

**The assumption:** NAV invoice line items + VTSZ-prefix matching against the EPR config's `vtszMappings` yields correct EPR packaging quantities.

**Where it holds:** Companies whose *product is packaging material itself* (paper-bag distributors, PET bottle sellers, carton sellers). For them, the VTSZ on the invoice *is* the packaging material; quantity on the invoice *is* the EPR-reportable quantity.

**Where it breaks (the actual ICP):** Every Hungarian KKV manufacturer or importer whose invoices list *packaged products* rather than packaging materials. A food producer invoices "Danone Activia 4×125g — 100 units"; the EPR-reportable materials (PP cup 0.7 kg, Al foil 0.1 kg, paper 0.35 kg) appear nowhere on the invoice. The registry of per-product packaging composition — the legally required *csomagolási nyilvántartás* under 80/2023. (III. 14.) Korm. rendelet — is the missing join table.

**Confirmation from research:**

- The registry-as-per-SKU-bill-of-materials pattern is **universal** across mature EU schemes (Germany LUCID, France CITEO, all commercial EPR software vendors).
- A direct Hungarian competitor, **Körforgó** (korforgo.hu, 10–15 000 Ft/quarter), already implements this registry + auto-compute pattern — but has three documented gaps: no NAV Online Számla integration, no KF-code discovery aid, no public demo content.
- The **invoice-driven auto-mapping angle** is genuinely unique to the Hungarian market — no EU scheme or vendor ingests invoice data natively.

**ICP confirmed by PO:** every KKV manufacturer/importer. The current Story 8.3 behaviour is therefore correct *only* for a minority of the target market; for the majority, the registry is non-negotiable.

---

## 2. Impact Analysis

### Epic Impact

**New Epic 9: Product-Packaging Registry & Automated EPR Filing** — four new stories establishing the registry data model, NAV-driven bootstrap, AI-assisted classification, and MOHU-compliant CSV export.

**Epic 8 (in progress):** Story 8.3 remains — it does not get rolled back. Its VTSZ-prefix logic is re-scoped as a **fallback layer** inside Epic 9's classification pipeline (§4.2). One immediate patch to its UI copy (§4.1).

**Completed epics (1–7):** No rollback. The screening, watchlist, GDPR, admin and i18n surfaces are unaffected.

### Story Impact

| Story | Change | Why |
|---|---|---|
| 8.3 | **Immediate UI-copy patch** on `InvoiceAutoFillPanel.vue` — set honest expectations for current users | Prevents wrong-segment users from filing bad bevallás based on current behaviour |
| 8.3 | **Later re-scope** as "VTSZ fallback" layer inside Story 9.3's classifier pipeline | Preserves existing work; gives it a well-defined role |
| *new* 9.1 | Product-Packaging Registry foundation (data model + CRUD UI) | Legal join table between invoice quantity and EPR material mass |
| *new* 9.2 | NAV-invoice-driven registry bootstrap + triage UI | Differentiator vs Körforgó; solves the blank-page problem |
| *new* 9.3 | AI-assisted KF-code classification (Claude Haiku, confidence-gated, user-confirms) | Solves Körforgó's biggest UX gap (no KF discovery aid) |
| *new* 9.4 | Registry-driven EPR autofill + MOHU CSV export | Closes the loop: invoice → registry → bevallás |

### Artifact Conflicts

| Artifact | Impact | Action Needed |
|---|---|---|
| **PRD** (`prd.md`) | No explicit product-registry feature | Add FR for Product-Packaging Registry; reference 80/2023 Annex 3+4+1 legal basis |
| **Architecture** (`architecture.md`) | No AI model call path, no pluggable EPR report target, no supplier-declaration provenance | New ADR for AI classification path; pluggable `EprReportTarget` interface; provenance audit fields |
| **Epics** (`epics.md`) | Epic 8 header lists only Stories 8.1–8.2; Epic 9 does not exist | Add Epic 9 heading + stories; update Epic 8 header to include 8.3–8.6 |
| **UX Design** (`ux-design-specification.md`) | No registry UX, no triage queue UX | Add screens: registry list, product editor with composition BoM, NAV-bootstrap triage |
| **Project context** (`project-context.md`) | No guidance on AI model usage, confidence thresholds, audit trail requirements | New section: AI-assisted classification ground rules |
| **DB schema** | No `products` / `product_packaging_components` tables | New Flyway migrations in Story 9.1 |
| **Gap doc** (`epr-packaging-calculation-gap-2026-04-14.md`) | Mislocates registry fields in 80/2023 Annex 3 points 2.2/2.3 (those are battery-specific; amended by 203/2025 for batteries only) | Add correction note referencing Annex 3.1 + Annex 4.1 + Annex 1.2 as correct legal basis |

### Technical Scope Assessment

- **DB migrations:** New tables — `products`, `product_packaging_components`, `registry_entry_audit_log`. All tenant-scoped per existing multi-tenancy convention.
- **Backend:** New `ProductRegistryService`, `RegistryBootstrapService` (reuses existing NAV invoice fetcher), `KfCodeClassifierService` (Claude Haiku integration), extended `EprService.autoFillFromInvoices()` to use registry with VTSZ-prefix fallback, new `MohuCsvExporter`.
- **Frontend:** New module `pages/registry/*`, triage UI component, product editor with multi-component BoM, progress indicators for AI calls.
- **Config:** New env vars for AI model selection, rate limits, confidence threshold, per-tenant monthly call cap.
- **External dependency:** Vertex AI (Gemini 2.5 Flash) as the only AI classifier for MVP. IAM + workload identity — no API key to store or rotate. Endpoint pinned to EU region (`europe-west1` / equivalent) for data residency. OpenRouter / alternative providers explicitly deferred (see §4.4 non-goals).
- **Test impact:** Unit tests for classifier with mocked AI; integration tests for triage flow; e2e test for full invoice→registry→CSV path. ArchUnit rules for the pluggable report target boundary.

---

## 3. Recommended Approach

**Direct Adjustment with new Epic 9 — RECOMMENDED**

Do not roll back Story 8.3. Do not attempt to retrofit registry logic into Epic 8 — it deserves its own epic scope. Add one immediate small patch to 8.3 (UI copy) and open Epic 9 with four sequential stories.

**Rationale:**

- Story 8.3 was correctly scoped for one market segment and remains useful there + as fallback.
- The registry is a foundation-level feature; bundling it into Epic 8 inflates Epic 8's scope past coherent review.
- Research confirmed the pattern: invoice data + registry + AI classification is the unique differentiator. Every month of delay is a month Körforgó holds market share alone.

**Alternatives considered:**

- **Rollback of Story 8.3** — rejected. It works for a real segment and its logic has a future role as fallback.
- **Wait for PPWR implementing act (2026-02-12 deadline, main application 2026-08-12)** — rejected. Waiting costs market position; the five decoupling principles in §5 let us build now and migrate without major rework.
- **Skip AI classification; ship registry + bootstrap only** — rejected. Without the KF-code helper, user onboarding friction matches Körforgó's, eliminating our biggest UX wedge.

**Effort / risk / timeline:**

- Effort: 4 new stories, est. 3–5 days each → ~3 weeks total, sequenced.
- Risk: Medium. AI hallucination risk mitigated by confidence gating + mandatory user confirmation (§5.3). Cost risk negligible — see §6.3.
- Timeline: Story 9.1 can start immediately; 9.2 depends on 9.1; 9.3 depends on 9.1; 9.4 depends on 9.1, 9.2, 9.3.

---

## 4. Detailed Change Proposals

### 4.1 Immediate patch — Story 8.3 UI copy

**File:** `frontend/app/components/epr/InvoiceAutoFillPanel.vue` (and i18n entries hu/en)

**OLD (paraphrased intent):** "Auto-filled from invoices" — no scope caveat

**NEW:** Banner copy stating:

> *"Az automatikus számla-alapú kitöltés azokhoz a vállalkozásokhoz illik, akik elsődlegesen csomagolóanyagokat (zsák, palack, doboz) forgalmaznak. Ha az Ön számláin csomagolt termékek szerepelnek, állítsa be a Termék-Csomagolás Nyilvántartást a pontos EPR számításhoz."*

English equivalent in `en.json`. Link to (future) registry module when available.

**Effort:** <1 hour. Ship this independently, immediately.

### 4.2 Story 9.1 — Product-Packaging Registry Foundation

**Goal:** Per-tenant CRUD for products with multi-component packaging bill-of-materials. Legally grounded in 80/2023 Annex 3.1 (registration identity), Annex 4.1 (per-transaction reporting), Annex 1.2 (KF code structure).

**Data model (proposed):**

- `products` (tenant_id, id, article_number, name, vtsz, primary_unit, status, created_at, updated_at)
- `product_packaging_components` (id, product_id, material_description, kf_code, weight_per_unit_kg, component_order, **recyclability_grade** nullable, **recycled_content_pct** nullable, **reusable** nullable, **substances_of_concern** nullable, **supplier_declaration_ref** nullable, created_at, updated_at)
- `registry_entry_audit_log` (id, product_id, field_changed, old_value, new_value, changed_by_user_id, source: `MANUAL`/`AI_SUGGESTED_CONFIRMED`/`AI_SUGGESTED_EDITED`/`VTSZ_FALLBACK`/`NAV_BOOTSTRAP`, timestamp)

**Acceptance criteria (summary):**
- Registry list page with search/filter by name, VTSZ, KF code, status.
- Product editor supporting 1..N packaging components, each with full KF code (8-digit structured input per Annex 1.2).
- PPWR-ready nullable fields present in schema from day one (see §5 invariants).
- Full audit trail for every field change (supports MOHU audit).
- ArchUnit rule: no service outside `registry` package writes to `product_packaging_components`.

### 4.3 Story 9.2 — NAV-Invoice-Driven Registry Bootstrap + Triage UI

**Goal:** On first registry setup (or on demand), pull the tenant's last 6–12 months of outbound NAV invoices, deduplicate line items by (product_name, VTSZ), and present as candidate products in a triage UI.

**Flow:**
1. User triggers "Bootstrap from NAV invoices" or the system offers it on empty registry.
2. `RegistryBootstrapService` fetches invoices via existing NAV client (Story 8.1 infrastructure).
3. Deduplicates line items; ranks by frequency and total quantity.
4. For each candidate, calls Story 9.3 classifier to pre-suggest KF code + component structure.
5. Candidates land in triage queue with states: `PENDING`, `APPROVED`, `REJECTED_NOT_OWN_PACKAGING`, `NEEDS_MANUAL_ENTRY`.
6. User goes through triage at own pace; approved items materialize as `products` rows.

**Acceptance criteria (summary):**
- Dedup logic handles slight name variations (trim, case, whitespace).
- Triage queue persists across sessions.
- Skip/reject states kept for audit (no silent data loss).
- No AI call made for already-rejected items.

**UX note:** This is the main wedge vs Körforgó. Deserves dedicated UX pass — triage must feel faster than manual entry.

### 4.4 Story 9.3 — AI-Assisted KF-Code Classification

**Goal:** Given a product (name, VTSZ, optional description), suggest KF code(s) and likely packaging components with confidence scores. User confirms before save.

**Architecture:**
- `KfCodeClassifierService` strategy interface — minimal abstraction kept so an alternative AI provider can be added later without refactoring.
- **AI implementation (only one for MVP):** `VertexAiGeminiClassifier` — Gemini 2.5 Flash via Vertex AI, pinned to EU region, IAM-authed via workload identity (no API key). System prompt carries a pruned KF taxonomy excerpt (Annex 1).
- **Fallback (existing):** `VtszPrefixFallbackClassifier` — Story 8.3's VTSZ-prefix matcher against `vtszMappings` (renamed, reused as-is).
- Output: `ClassificationResult { suggestions: List<KfSuggestion>, confidence: HIGH/MEDIUM/LOW, strategy: VERTEX_GEMINI | VTSZ_PREFIX | NONE, modelVersion, timestamp }`.
- If Gemini is unavailable or returns below the confidence threshold, the router falls through to VTSZ-prefix; if that too produces nothing, the field is left empty for manual entry.

**Explicit non-goals for MVP (deferred):**
- OpenRouter integration — not built now. The abstraction allows dropping it in later as a sibling of `VertexAiGeminiClassifier` if Gemini accuracy turns out inadequate for Hungarian inputs, or as a cross-region fallback post-MVP.
- A-B testing harness — not built now. If accuracy validation (§8.6) shows Gemini Flash passing, we ship without comparison plumbing.

**Why Gemini via Vertex AI is the choice:**
- Same cloud, same region → ≤200ms latency, traffic stays on GCP VPC.
- IAM + workload identity → no stored API key, nothing to rotate.
- EU data residency by endpoint pinning → cleaner GDPR / MOHU audit story for Hungarian SMEs.
- Cost ~7× lower than Claude Haiku for the same task (see §6.3).
- Task fit — small structured classification, Flash class is sufficient.

**Guardrails:**
- Confidence threshold below which no suggestion is shown (user enters manually).
- Per-tenant monthly call cap (configurable; default included in base pricing tier — see §8.4).
- Circuit breaker on the Gemini strategy; router skips to VTSZ-prefix until healthy.
- All AI suggestions tagged `AI_SUGGESTED_CONFIRMED` or `AI_SUGGESTED_EDITED` in audit log, with model version and timestamp.
- System prompt includes explicit instruction: only suggest, never assert; flag uncertainty.

**Cost envelope (confirmed in research §6.3):**
- Gemini 2.5 Flash: ~$0.075/M input + $0.30/M output → **~$0.0004 per classification ≈ 0.15 Ft**.
- 200-product bootstrap per tenant: **~30 Ft one-time**.
- Annual ongoing per tenant (~50 new products): **~8 Ft/year**.

**Acceptance criteria (summary):**
- Mocked classifier in unit tests; real Vertex AI only in dedicated integration tests behind an env flag.
- Graceful degradation: Vertex AI outage → VTSZ-prefix fallback → empty field, never block user.
- Cost meter visible to PLATFORM_ADMIN (per-tenant monthly spend).
- Accuracy validation harness: 50–100 held-out labelled items; pass bar = ≥70% top-1 first-click acceptance (validated pre-prod — see §8.6).

### 4.5 Story 9.4 — Registry-Driven EPR Autofill + MOHU CSV Export

**Goal:** When the tenant requests their quarterly EPR report, the system:
1. Pulls outbound invoices for the period
2. For each line item: if product is in registry → multiply units × component weights (grouped by KF code); else fall back to VTSZ-prefix match (Story 8.3 logic)
3. Aggregates totals per KF code, in kg with 3 decimals
4. Produces the MOHU-compatible export (format to be pinned once we confirm current OKIRkapu submission format; assumed CSV for now per PO statement)

**Pluggable report target (PPWR-decoupling invariant §5.3):**
- `EprReportTarget` interface with implementations: `MohuCsvExporter` (today), `EuRegistryAdapter` (post-2029 placeholder)
- `EprService` never references `MohuCsvExporter` directly

**Acceptance criteria (summary):**
- Report shows per-line-item provenance: registry-match / VTSZ-fallback / unmatched
- Unmatched lines flagged prominently with prompt to register the product
- CSV matches current MOHU-acceptable format (to be verified — see §8.5 Action items)
- Export includes a human-readable summary report alongside the machine CSV

---

## 5. Architectural Invariants Introduced (PPWR Decoupling)

These become binding architectural rules for Epic 9 and all future EPR work. Rationale: PPWR (EU Regulation 2025/40) applies from 2026-08-12, with Commission implementing act for EU registry format due 2026-02-12, and single EU registry replacing national registries by 2029. Without these invariants, Epic 9's work needs major rewrites in 2–4 years.

1. **Packaging is modelled at SKU/component level, never as aggregate tonnage.** Aggregation happens at report generation time, not at storage time.
2. **KF code is one classifier among many** on `product_packaging_components`. Nullable PPWR fields (`recyclability_grade`, `recycled_content_pct`, `reusable`, `substances_of_concern`) exist in schema from Story 9.1 onwards, even though they are not populated today.
3. **The EPR report target is pluggable.** `EprService` depends on `EprReportTarget` interface only. MOHU is today's implementation; EU-registry adapter is a future implementation. No service references MOHU as system of record.
4. **Fee modulation rules are data, not code.** Any future eco-modulation (mandated PPWR 2030) is loaded from config/DB, not branched in logic.
5. **Supplier declaration provenance is captured from day one.** `supplier_declaration_ref` nullable field on components; even if unused today, backfilling chain-of-custody evidence later is prohibitive.

ArchUnit rules enforce (1), (3), (4). Code review catches (2), (5).

---

## 6. Research Appendix (evidence base)

### 6.1 Hungarian market findings

- **80/2023 Annex 3.1 + Annex 4.1 + Annex 1.2** are the correct legal basis for the producer packaging data model. The gap doc's reference to Annex 3.2/3.3 was wrong — those points cover batteries (amended by 203/2025).
- **MOHU** does not publish a machine-readable registry schema (XSD/CSV). Submission is via OKIRkapu, not MOHU directly. MOHU only issues the quarterly fee invoice.
- **Körforgó** (Helion Kft.): direct competitor, 10 000 Ft/q (EPR), 15 000 Ft/q (EPR+KVTD). Per-SKU BoM data model. Three documented gaps: no NAV integration, no KF discovery aid, no public demo.
- **MultiSoft HEP** (Dynamics 365 Business Central HU localization) also advertises an EPR module — primarily enterprise, not SME.
- Most Hungarian SMEs currently use **Excel + manual OKIRkapu entry**. No dominant template.
- Recent regulatory activity: 203/2025 amendment (battery-focused), PPWR entered into force 2025-02, KF codes refresh scheduled 2026-01-01.

### 6.2 EU comparables

- **LUCID (Germany):** XML/XSD format, kg with 3 decimals, ~9 material categories.
- **CITEO (France):** Barème F = weight × material rate + per-unit × 6 sectors; portal-only, no public API.
- **Commercial EPR software (Ecoveritas, Lorax EPI, RLG):** universal pattern = per-SKU BoM with {material, weight, resin, recyclability}. ISO 1043 codes common.
- **No EU scheme ingests invoice data natively.** Hungary's NAV Online Számla-driven angle is genuinely unique.

### 6.3 AI cost envelope

Per-classification token budget: ~3 000 input + ~400 output tokens.

**Chosen model — Gemini 2.5 Flash via Vertex AI:** $0.075/M input + $0.30/M output → **~$0.0004 per call (~0.15 Ft)**.

- 200-product bootstrap per tenant: **~30 Ft one-time**.
- Annual ongoing per tenant (~50 new products): **~8 Ft/year**.
- Effectively zero against target pricing (10–40 000 Ft/quarter).

For reference only (alternatives considered but deferred — see §4.4 non-goals): Claude Haiku 4.5 ~7× more expensive at ~1.8 Ft/call; GPT-4o-mini ~1.6× at ~0.25 Ft/call. Both still negligible in absolute terms.

### 6.4 PPWR timeline (practical impact)

| When | What |
|---|---|
| 2026-01-01 | KF code refresh (HU domestic, independent of PPWR) |
| 2026-02-12 | Commission implementing act for EU producer register format due |
| 2026-08-12 | PPWR applies: PFAS ban in food-contact, recyclability obligations begin |
| ~2027 | National registers align with EU format (18 months after Feb-2026 act) |
| 2028 | Harmonised Design-for-Recycling delegated acts; labelling rollout |
| 2029 | Single EU producer registry replaces national ones |
| 2030 | Eco-modulated EPR fees mandatory, keyed to A/B/C/D recyclability grades |

### 6.5 Primary sources
- `https://magyarkozlony.hu/dokumentumok/a771b92fabdcea3b8a826ab959f5d9e67f511ea8/letoltes` (MK 2023/37, original 80/2023)
- `https://magyarkozlony.hu/dokumentumok/57e5f77c31c604dfc48bb20b3cf536685c2678c3/letoltes` (MK 2025/85, 203/2025 amendment)
- `https://eur-lex.europa.eu/eli/reg/2025/40/oj/eng` (PPWR)
- `https://korforgo.hu/tudasbazis`, `https://korforgo.hu/arak`
- `https://www.verpackungsregister.org/en/registration/find-out-about-registrations/using-an-xml-interface-to-upload-your-data` (LUCID XML)
- Full list in research session logs

---

## 7. Implementation Handoff

**Change scope classification:** **Major** — new epic, new data model, new architectural invariants, new external dependency (Anthropic API).

### 7.1 Routing

- **Product Manager (John):** Update PRD with Product-Packaging Registry FR; confirm ICP statement; sign off on pricing implications of AI cost pass-through (none expected — AI cost is noise relative to quarterly fee).
- **Architect (Winston):** Author ADR for AI classification path — **Vertex AI / Gemini 2.5 Flash pinned to EU region with workload-identity auth; VTSZ-prefix matcher as fallback; OpenRouter / alternative providers explicitly deferred but interface designed to admit them later**. Cover confidence threshold, circuit breaker, per-tenant monthly cap, audit fields. Also author ADR for pluggable `EprReportTarget`; update `architecture.md` with registry data model diagram; update ArchUnit rules.
- **UX Designer (Sally):** Design screens for registry list, product editor with BoM, NAV-bootstrap triage queue. Special attention to triage UX — this is the wedge vs Körforgó.
- **Scrum Master (Bob):** Add Epic 9 to `epics.md`; create story skeletons for 9.1–9.4; schedule Epic 9 into sprint plan after current Epic 8 close.
- **Dev (Amelia):** Immediate — ship §4.1 UI copy patch today. After Epic 8 close — pick up Story 9.1.

### 7.2 Deliverables from this proposal

- [x] Sprint Change Proposal document (this file)
- [ ] PRD update (PM)
- [ ] Epics.md update with Epic 9 + stories 9.1–9.4 (SM)
- [ ] Architecture ADR for AI classification (Architect)
- [ ] Architecture ADR for pluggable report target (Architect)
- [ ] UX mocks for registry + triage (UX Designer)
- [ ] Gap doc correction note re: Annex 3.1/4.1/1.2 (SM)
- [ ] Story 8.3 UI copy patch — immediate (Dev)

### 7.3 Success criteria

- A Hungarian KKV manufacturer can bootstrap their registry from NAV invoices in ≤30 minutes of triage work for a 200-SKU catalogue.
- AI KF-code suggestions (Gemini Flash primary) have ≥70% first-click acceptance rate — validated first on a 50-100 item held-out labelled set pre-prod, then monitored over 2 weeks of real-tenant usage.
- Generated MOHU CSV passes OKIRkapu upload validation first try, zero manual correction.
- PPWR readiness self-assessment: given the Commission's Feb-2026 implementing act, our data model needs no schema migration — only new field population.

---

## 8. Open Questions / Action Items Before Epic 9 Starts

1. **Confirm OKIRkapu submission format.** PO's recollection is CSV; needs verification with fresh MOHU/OKIR documentation. Story 9.4 format depends on this.
2. **Confirm KF code 2026-01-01 refresh scope.** If KF code IDs change in a way that breaks stored codes, a migration plan is needed before 9.1 goes to production.
3. **Vertex AI governance.** Confirm workload-identity binding from the GCP runtime (Cloud Run / GKE), pin endpoint to EU region, request Gemini Flash quota increase if needed.
4. **Pricing-tier decision for AI usage.** Current Gemini Flash cost (~0.15 Ft/call, ~30 Ft / 200-SKU bootstrap) is so low that tier-gating is unnecessary — include in base tier. Only reconsider if someone pivots the primary model to Claude/GPT.
5. **Körforgó competitive watch.** Schedule a follow-up (quarterly?) on Körforgó's feature evolution — especially if they add NAV integration, our primary differentiator narrows.
6. **Accuracy validation set for Gemini Flash on KF classification.** Before Story 9.3 integration work, build a 50–100 item hand-labelled held-out set (product name + VTSZ + correct KF). Run Gemini 2.5 Flash with the prompt; accept if ≥70% top-1 on Hungarian inputs. If it fails the bar, that becomes the trigger to revisit the OpenRouter non-goal. ~1 hour effort; de-risks the whole story.

---

**End of Sprint Change Proposal CP-5.**
