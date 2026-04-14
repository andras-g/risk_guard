# Task 03: Architecture — Two ADRs + Registry Data Model Diagram

**Goal:** Produce two Architecture Decision Records and update `architecture.md` with the Product Registry data model.
**Scope:** Planning document work via BMad skill.
**Skill:** `bmad-create-architecture` or direct ADR authoring (Architect agent Winston).
**Prerequisite:** Task 01 (OKIRkapu + KF research) complete. Task 02 (PRD update) preferably done but can run in parallel.
**Estimated effort:** 2 hours.

---

## Prompt for the new session

```
/bmad-create-architecture

Context: Sprint Change Proposal CP-5 (_bmad-output/planning-artifacts/sprint-change-proposal-2026-04-14.md) was approved and routes architecture work to Winston. I need:

1. ADR-XX-ai-kf-classification.md — new ADR covering the AI classification path
2. ADR-XX-pluggable-epr-report-target.md — new ADR covering the report-target abstraction
3. architecture.md — data model diagram update for the Product Registry (new tables + relationships)
4. ArchUnit rules — additions to enforce §5 invariants

=== ADR 1: AI KF Classification ===

Decision: Gemini 2.5 Flash via Vertex AI, pinned to EU region, workload-identity auth, with VTSZ-prefix fallback.

Must cover:
- Why Vertex AI / Gemini (not Anthropic direct, not OpenRouter) — see CP-5 §4.4 "Why Gemini via Vertex AI is the choice"
- KfCodeClassifierService interface — minimal, strategy-pattern so alternative providers are additive later
- Two implementations for MVP: VertexAiGeminiClassifier + VtszPrefixFallbackClassifier (reuses Story 8.3 logic)
- Explicitly out of scope: OpenRouter, A-B harness (deferred per CP-5 §4.4 non-goals)
- Confidence threshold handling (below threshold → no suggestion shown, not a wrong one)
- Circuit breaker on the Gemini strategy
- Per-tenant monthly call cap (configurable)
- Audit trail fields: strategy, modelVersion, timestamp
- System prompt design constraints (only suggest, never assert; flag uncertainty)
- Test strategy: mocked classifier in unit tests, real Vertex AI only in env-flag-gated integration tests
- EU data residency (endpoint pinning + regional latency expectations)

Trigger to revisit: if Gemini Flash fails on validation set (see CP-5 §8.6), escalate to PO before merging Story 9.3.

=== ADR 2: Pluggable EPR Report Target ===

Decision: EprService depends on EprReportTarget interface; MOHU is today's implementation, EU-registry adapter is a future one. MOHU is not the system of record.

Must cover:
- EprReportTarget interface shape (what methods, what return types)
- MohuCsvExporter implementation — format per Task 01 research findings (CSV assumed until research confirms otherwise)
- Future EuRegistryAdapter placeholder (post-2029 per PPWR timeline)
- Provenance recording: every line in the export carries registry-match / VTSZ-fallback / unmatched tags
- ArchUnit rule: no service outside the registry/export packages may reference MohuCsvExporter directly — only via EprReportTarget
- Rationale tied to PPWR timeline (CP-5 §5 invariant 3)

=== architecture.md updates ===

Add a new section (or update existing data model section) with:
- products table (tenant_id, id, article_number, name, vtsz, primary_unit, status, created_at, updated_at)
- product_packaging_components table (id, product_id, material_description, kf_code, weight_per_unit_kg, component_order, recyclability_grade NULL, recycled_content_pct NULL, reusable NULL, substances_of_concern NULL, supplier_declaration_ref NULL, created_at, updated_at)
- registry_entry_audit_log table (id, product_id, field_changed, old_value, new_value, changed_by_user_id, source, timestamp)
- ERD-style relationships with existing tenants table
- Note on PPWR nullable fields — populated later, schema stable now

=== ArchUnit rule additions ===

1. No service outside the registry package writes to product_packaging_components.
2. EprService depends only on EprReportTarget interface, not concrete implementations.
3. Fee modulation rules are loaded from config/DB, not branched in code (CP-5 §5 invariant 4).

Reference artefacts to load:
- _bmad-output/planning-artifacts/sprint-change-proposal-2026-04-14.md (authoritative)
- _bmad-output/planning-artifacts/architecture.md (current)
- _bmad-output/planning-artifacts/research/okirkapu-and-kf-refresh-2026-04-XX.md (OKIRkapu format shapes MOHU exporter)
- docs/architecture/adrs/ — look at existing ADRs for format convention
```

## Success criteria

- Two ADRs committed under the existing ADR folder convention.
- `architecture.md` has a new or updated section showing the Registry data model.
- ArchUnit rules enforced for invariants 1, 3 (and ideally 4) from CP-5 §5.
- Each ADR has: context, decision, consequences, alternatives considered (and why rejected), revisit triggers.

## Do NOT

- Invent a different primary AI model. The Gemini decision is already made in CP-5.
- Design the OpenRouter fallback — it is explicitly deferred.
- Rewrite architecture.md from scratch — this is additive.
