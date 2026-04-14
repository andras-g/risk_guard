# EPR Packaging Calculation — Business Gap Analysis
**Date:** 2026-04-14
**Authors:** Bob (SM), Amelia (Dev), domain review with Andras
**Status:** Findings captured — awaiting epic scoping

---

## Background

Story 8.3 (Invoice-Driven EPR Auto-Fill) was implemented under the assumption that NAV invoice data can drive EPR packaging quantity calculations. During a domain review session, a critical gap was identified in that assumption.

---

## Finding 1 — The invoice autofill works for only one segment

### What Story 8.3 actually does

The current implementation:
1. Fetches OUTBOUND invoice line items with VTSZ codes
2. Groups by VTSZ code and sums quantities
3. Matches VTSZ codes against the EPR config's `vtszMappings` table (longest-prefix match)
4. Returns suggested KF-code and aggregated quantity

### Where this is correct

Companies whose **product is packaging material itself** — e.g., a company selling paper bags (VTSZ 4819), PET bottles (VTSZ 3923), or cardboard boxes (VTSZ 4819). For these, the VTSZ code on the invoice *is* the packaging material, and quantity on the invoice *is* the EPR-reportable quantity.

### Where this breaks down

For any manufacturer or importer selling products that *contain* packaging, invoices record **what was sold** (the product), not **what packaging came with it**.

**Example:** A food company invoices "Danone Activia joghurt 4×125g — 100 units" (VTSZ 04031000). The EPR-reportable materials are:
- 100 × 7g PP cup = 0.700 kg polypropylene
- 100 × 1g Al foil lid = 0.100 kg aluminium
- 100 × 3.5g cardboard holder = 0.350 kg paper

None of this appears anywhere in the NAV invoice data.

### Current noise behaviour

Invoice line items with no matching `vtszMapping` are not filtered out — they appear in the UI with `suggestedKfCode = null` and the raw VTSZ code as description. This produces noisy results for mixed-product invoices (hammer, screws, chain, etc. all appear).

---

## Finding 2 — The csomagolási nyilvántartás is a separate legal obligation

Under **80/2023. (III. 14.) Korm. rendelet** on Extended Producer Responsibility, every Hungarian *első forgalmazó* (first domestic distributor — manufacturer or importer) is legally required to maintain a packaging composition registry (*csomagolási nyilvántartás*).

### Key facts

| Property | Reality |
|---|---|
| Legally required | Yes — for all első forgalomba hozók |
| Standardised format | **No** — no MOHU-mandated schema or file format |
| Digitised centrally | **No** — not collected by MOHU; each company maintains their own |
| Where it lives | Company ERP (SAP, Oracle, Dynamics), custom Excel, paper records |
| MOHU access | Audit-on-request only; not submitted routinely |
| Content | Per-product: material type, weight per unit, KF code |

This registry is the **authoritative source of truth** for EPR calculations. Invoices can supply *quantity of products sold*, but the registry supplies *how much packaging was on each product*.

### Legal formula

```
EPR quantity [kg] = Σ (units sold from invoice × packaging weight per unit from registry)
                     grouped by KF code (material category)
```

The current system has no registry. It attempts to substitute VTSZ-prefix matching for what should be a product-level packaging lookup.

---

## Finding 3 — AI-assisted classification: promising but bounded

During the session, AI-based classification was evaluated as a potential solution.

### What a small AI model can do

| Task | Feasibility | Notes |
|---|---|---|
| Is this product EPR-relevant? | ✓ Feasible | Smarter VTSZ triage, handles free-text names |
| What material category is the packaging? | ✓ Probably | Classification only, not weights |
| What does the packaging weigh per unit? | ✗ Not reliable | Requires real product spec data; hallucinated weights create legally incorrect filings |
| Replace the nyilvántartás? | ✗ No | Legal obligation remains regardless |

### Viable architecture

AI works as a **suggestion layer that pre-populates the registry** — not a direct path to EPR filing:

```
Invoice line item (VTSZ + description + qty)
        ↓
AI classifies: "food product — likely PP cup + Al foil + paper multipack"
        ↓
Pre-fills product registry entry (draft state, user must confirm)
        ↓
User corrects/confirms weights from actual product spec sheet
        ↓
Registry entry saved → becomes the audit trail
        ↓
Future invoices for same product → registry lookup only, no AI call
```

**Audit trail note:** MOHU audits require documented evidence for every packaging weight figure. AI-suggested weights that are confirmed and saved by the user are defensible. AI weights applied directly to filings without human confirmation are not.

### Model options

- **Zero-shot frontier model** (Claude Haiku, GPT-4o-mini): Best quality, per-call cost, no training needed. Good for v1.
- **Fine-tuned small model** (Llama 3.1 8B, Phi-3): Cheaper at scale, requires Hungarian product-packaging training data (sparse).
- **Hybrid**: Frontier model per new product registration, cached in registry; VTSZ prefix matching for known categories at query time.

---

## Current state of Story 8.3 — honest scope statement

Story 8.3 is **not wrong** — it delivers real value for the right segment. What was missing is explicit scope labelling.

**Works well for:** Companies whose primary business is selling packaging materials (distributors of bags, bottles, boxes, wrapping film).

**Not suitable for:** Manufacturers, food producers, importers, or any company where packaged products are on the invoice rather than the packaging materials themselves.

**Immediate mitigation (low effort):** Update the UI copy in `InvoiceAutoFillPanel.vue` to set accurate expectations. Something like: *"Best suited for businesses that sell packaging materials directly. If your invoices list packaged products, set up your Product Registry first to define packaging compositions."*

---

## Gap Summary — What is missing

### Gap A: Product-Packaging Registry (Termék-Csomagolás Nyilvántartás)
A first-class feature allowing SME users and accountants to register products and their packaging compositions (material type, weight per unit, KF code). This becomes the join table between invoice quantities and EPR filing quantities.

**Required for:** Correct EPR calculation for any manufacturer or importer.
**Replaces:** The assumption that VTSZ-prefix matching alone is sufficient.

### Gap B: Registry-Driven Invoice Autofill
Update `EprService.autoFillFromInvoices()` to join invoice line items against the Product Registry (by VTSZ code or product name), multiply quantities by per-unit packaging weights, and aggregate by KF code. The current VTSZ-prefix match remains as a fallback for unmapped products.

### Gap C: AI-Assisted Registry Pre-Population (Optional Enhancement)
Add an AI call (frontier model) when a user adds a new product to the registry. The model suggests likely packaging components based on product name + VTSZ code. User reviews and confirms before saving. Eliminates the blank-page problem for initial registry setup.

**Dependency:** Gap A must exist before Gap C is meaningful.

---

## Recommended Sequencing

1. **Immediate (no story):** Update `InvoiceAutoFillPanel.vue` UI copy to set honest expectations for current users.
2. **Story A:** Product-Packaging Registry — CRUD UI + backend + data model. Foundation for correct EPR calculation.
3. **Story B:** Registry-Driven Invoice Autofill — wire `autoFillFromInvoices` to use registry when product is known, VTSZ prefix as fallback.
4. **Story C (optional):** AI-Assisted Registry Pre-Population — Claude Haiku call on new product registration to suggest packaging components.

Stories A + B together constitute the legally correct EPR calculation path.
Story C is a UX enhancement that reduces onboarding friction for the registry.
