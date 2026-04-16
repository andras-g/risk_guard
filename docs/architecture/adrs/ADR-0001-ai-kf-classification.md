# ADR-0001: AI-assisted KF-code classification path

- **Status:** Accepted
- **Date:** 2026-04-14
- **Deciders:** Andras (PO), Winston (Architect), Bob (SM), Amelia (Dev)
- **Related artefacts:**
  - `_bmad-output/planning-artifacts/sprint-change-proposal-2026-04-14.md` (CP-5, §4.4)
  - `_bmad-output/planning-artifacts/research/okirkapu-and-kf-refresh-2026-04-14.md`
  - `_bmad-output/planning-artifacts/epr-packaging-calculation-gap-2026-04-14.md`
  - PRD §FR (Product-Packaging Registry feature, added by Task 02)

## Context

Epic 9 introduces a Product-Packaging Registry whose central usability bet is that the system can suggest a likely 8-character KF code (and accompanying packaging-component structure) for each product the user registers. Without that helper, onboarding a 200-SKU catalogue means looking up KF codes manually in 80/2023 Annex 1.2 — exactly the friction Körforgó (the direct competitor) leaves unsolved. With the helper, the user reviews and confirms suggestions instead of authoring them.

Constraints shaping the choice:

- **EU data residency.** Hungarian SME users plus the MOHU/OKIRkapu audit story require that product names, VTSZ codes, and any free-text descriptions sent to the model stay in EU regions. Anything that ships data to a US endpoint creates a GDPR question we do not want.
- **No stored API keys.** The platform already runs on GCP (Cloud Run / GKE planned). Adding a long-lived API secret for a non-GCP vendor means a rotation runbook, secret-manager wiring, and a credential-leak blast radius we currently do not have.
- **Cost must be near-zero against the target price.** Quarterly EPR pricing tiers run 10–40 000 Ft. AI cost per tenant must round to zero against that, including a one-time bootstrap of ~200 SKUs and ~50 new SKUs/year.
- **Hallucination is unacceptable in the user's submission path.** A wrong KF code in a producer's quarterly EPR submission is a regulatory error. The classifier cannot assert; it can only suggest, and the user must confirm before any value is persisted as ground truth.
- **Solo-developer maintenance budget.** Whatever we pick has to survive on <10 hrs/week of attention. No A/B harness, no multi-vendor router, no OpenRouter shim — these can be added later if needed.
- **Validation gate before commitment.** Per CP-5 §8.6, a 50–100 item hand-labelled held-out set must pass ≥70% top-1 first-click acceptance before Story 9.3 ships.

## Decision

**We use Gemini 2.5 Flash via Vertex AI, pinned to an EU region, authenticated by GCP workload identity, as the sole AI classifier for MVP. The classifier is invoked through a `KfCodeClassifierService` strategy interface; a `VtszPrefixFallbackClassifier` (the existing Story 8.3 VTSZ-prefix matcher, refactored as a strategy) is the fallback. OpenRouter, Anthropic-direct, and an A/B comparison harness are explicitly out of scope for MVP.**

The interface is the architectural contract; the chosen vendor is one strategy behind it. Switching or adding strategies later is additive and does not require touching `RegistryService`, `EprService`, or any caller.

### Component shape

```
hu.riskguard.epr.registry.classifier
├── KfCodeClassifierService        // strategy interface (sync, single-product)
├── ClassificationResult           // record: suggestions, confidence, strategy, modelVersion, timestamp
├── KfSuggestion                   // record: kfCode, components[], score
├── ClassificationStrategy         // enum: VERTEX_GEMINI | VTSZ_PREFIX | NONE
├── ClassificationConfidence       // enum: HIGH | MEDIUM | LOW
└── ClassifierRouter               // routes: Gemini → VTSZ-fallback → empty
                                   // applies confidence threshold + circuit breaker

hu.riskguard.epr.registry.classifier.internal
├── VertexAiGeminiClassifier       // KfCodeClassifierService impl, EU-pinned endpoint
└── VtszPrefixFallbackClassifier   // KfCodeClassifierService impl, reuses Story 8.3 logic
```

`RegistryService` and `RegistryBootstrapService` (Stories 9.1 / 9.2) depend on `KfCodeClassifierService` only. They never reference a concrete strategy.

### Vendor configuration

- **Model:** `gemini-3.0-flash-preview` via Vertex AI.
- **Region:** Pinned to `europe-west1` (or equivalent EU region with Gemini Flash quota). No fallback to non-EU regions even on Vertex outage — circuit breaker trips and the router falls through to VTSZ-prefix instead.
- **Auth:** Workload identity from the Cloud Run / GKE runtime service account. No API keys stored, no secrets to rotate.
- **Quota:** Default Gemini Flash quota is sufficient at expected volumes (~30 calls/tenant during bootstrap, ~50/tenant/year ongoing). Request quota increase only if multi-tenant onboarding spikes hit the limit.

### Guardrails

- **Confidence threshold.** If `ClassificationResult.confidence == LOW`, the router returns no suggestion (the field is left empty for manual entry). Threshold value lives in config; default tuned during the §8.6 validation pass.
- **Circuit breaker.** Resilience4j circuit breaker on the `VertexAiGeminiClassifier` strategy. While open, the router skips straight to `VtszPrefixFallbackClassifier`. Half-open probes per the existing project Resilience4j defaults (see `core/config/ResilienceConfig.java`).
- **Per-tenant monthly call cap.** Configurable cap (default included in base pricing tier — see CP-5 §8.4). Counter persisted; when the cap is reached, the router behaves as if Gemini is unavailable for the remainder of the month. Cap status surfaced to PLATFORM_ADMIN via the existing admin dashboard.
- **Audit trail.** Every persisted KF-code value carries a `source` enum: `MANUAL`, `AI_SUGGESTED_CONFIRMED`, `AI_SUGGESTED_EDITED`, `VTSZ_FALLBACK`, or `NAV_BOOTSTRAP`. AI-sourced rows additionally store `strategy`, `modelVersion`, and `timestamp` in `registry_entry_audit_log`.
- **System prompt constraints.** The Gemini system prompt explicitly instructs the model to (a) suggest, never assert; (b) flag uncertainty in its confidence score; (c) refuse to invent KF codes outside the provided taxonomy excerpt. Prompt and pruned KF taxonomy excerpt live in `backend/src/main/resources/prompts/`.
- **No free-text logging of model output.** The audit log stores the *chosen* KF code and the strategy, not the raw model response. Raw model responses appear in OpenTelemetry traces (sampled, retention-bounded) for debugging, not in the durable audit table.

### Test strategy

- **Unit tests** mock `KfCodeClassifierService` entirely. Routing logic, confidence handling, circuit-breaker behaviour, and audit-trail wiring are unit-testable without any network call.
- **Integration tests** that exercise the real `VertexAiGeminiClassifier` are gated behind an environment flag (`RG_INTEGRATION_VERTEX_AI=true`). They do not run in the default `./gradlew test` invocation, do not run in CI by default, and do not block local development. A nightly job (or manual pre-release run) executes them against a fixed 50-item validation set.
- **Validation harness** (CP-5 §8.6) — a hand-labelled 50–100 item set of (product name, VTSZ, correct KF code) lives under `backend/src/test/resources/kf-classifier-validation/`. Pre-release script measures top-1 first-click acceptance. Pass bar: ≥70%. Below the bar, escalate to PO before merging Story 9.3 (see Revisit triggers).

### EU data residency & latency

- Vertex AI Gemini calls stay on the GCP VPC, region-pinned. Expected latency: ≤200 ms per call within the same EU region.
- No data leaves the EU region at any point. The system prompt + product name + VTSZ are the only inputs sent; responses contain only KF codes and confidence metadata.
- For the GDPR / MOHU audit story: data sent to Vertex AI is technical metadata (product name, VTSZ), not personal data of the producer's customers. The producer's tax number is not sent.

## Consequences

### Positive

- **One AI dependency, one cloud, one IAM model.** No secret rotation, no cross-cloud egress, no separate monitoring stack.
- **Strategy interface preserves optionality.** If Gemini Flash underperforms on Hungarian inputs, dropping in a sibling strategy (`OpenRouterClassifier`, `ClaudeHaikuClassifier`, anything else) is purely additive — interface, router, and audit-trail wiring already exist.
- **Cost is genuinely negligible.** Per CP-5 §6.3, ~0.15 Ft per call → ~30 Ft per tenant bootstrap, ~8 Ft per tenant per year. Cost meter exists for visibility, but no per-tenant gating is needed.
- **Graceful degradation built in from day one.** Vertex AI outage, low confidence, or quota exhaustion all degrade to VTSZ-prefix fallback or empty field — never to a wrong suggestion or a blocked user.
- **Audit-trail design supports future regulator questions.** Every KF code in the registry can be traced to its source, including AI strategy and model version.

### Negative / accepted costs

- **Single-vendor exposure.** A Vertex AI region-wide outage means the classifier degrades to VTSZ-prefix for the duration. Acceptable because (a) VTSZ-prefix produces *something* useful for many products, (b) manual entry remains available, (c) the user is never blocked.
- **GCP lock-in deepens.** We were already on GCP for hosting, NAV integration, and DB. Adding Vertex AI tightens that, but it does not introduce a new lock-in vector.
- **No empirical comparison data.** Without an A/B harness, we have no ongoing evidence that Gemini Flash is the *best* choice — only that it passed the §8.6 validation bar. Acceptable for solo-dev MVP economics; revisit if accuracy degrades or competitor models leapfrog.
- **Validation set is small.** 50–100 items is enough to catch a model that is broadly wrong, not enough to catch subtle category bias. Mitigated by user-confirmation requirement on every suggestion.
- **Hungarian-language prompt design risk.** Gemini Flash's Hungarian-domain quality on EPR/KF taxonomy is unproven at the scale we will run it. The §8.6 validation gate is the only check before commitment.
- **Per-tenant cap requires bookkeeping.** Counter table + reset logic is small but real overhead; design it such that overshoot is harmless (extra calls = extra cost, never wrong behaviour).

## Alternatives considered

- **Anthropic Claude Haiku 4.5 (direct API).** ~7× more expensive at ~1.8 Ft/call (still negligible), but adds API-key management and US-region default. Rejected on the residency + secret-management grounds, not on cost. Could become the chosen sibling strategy if Gemini Flash fails §8.6 validation.
- **OpenRouter as a multi-vendor router.** Adds an extra hop, an extra vendor relationship, and an extra credential. Defers the residency story (depends on which model OpenRouter picks). Useful eventually as a way to A/B without provisioning each vendor directly. Rejected for MVP; the strategy interface allows adding it later as a sibling strategy if model diversity becomes valuable.
- **GPT-4o-mini.** Comparable cost (~0.25 Ft/call). Rejected on the same residency + secret-management grounds as Claude direct. Not strictly worse than Gemini Flash on quality for this task; chosen against to avoid widening vendor surface for no clear gain.
- **No AI; manual KF code entry only.** Removes a category of risk entirely. Rejected because it eliminates the primary UX wedge versus Körforgó (per CP-5 §3); without AI assistance, onboarding friction matches the competitor's.
- **A/B harness from day one.** Adds infrastructure to run two models in parallel and compare. Rejected for MVP — pure overhead until we have real-tenant traffic to compare on. Revisit when (a) a second strategy is added, and (b) tenant volume justifies the harness.
- **Self-hosted open-weight model (Llama, Mistral) on GCP.** Removes vendor dependency entirely. Rejected because GPU instances cost more than the entire annual Vertex AI bill per tenant by several orders of magnitude, and small-model accuracy on Hungarian KF taxonomy is unproven.

## Revisit triggers

Reopen this ADR if any of the following:

- **Validation gate fails.** Gemini Flash scores <70% top-1 on the §8.6 held-out validation set. Escalate to PO; the OpenRouter and Claude alternatives become live candidates.
- **Production accuracy degrades.** Real-tenant first-click acceptance drops below 60% over a rolling 4-week window after launch. Same alternatives become live candidates; consider building the A/B harness.
- **Vertex AI EU region availability degrades.** Sustained outages (>1% of calls failing over a month) or material latency regression (>500 ms p95) push the experience below the "feels faster than manual entry" bar.
- **Pricing changes materially.** Gemini Flash pricing rises by an order of magnitude. (Currently ~0.15 Ft/call; trigger is ~1.5 Ft/call where total annual cost per tenant approaches 100 Ft and monitoring becomes worth it.)
- **EU AI Act or GDPR guidance** introduces new requirements for AI-assisted regulatory submissions that Vertex AI EU-region cannot satisfy.
- **A second AI strategy is added** — at that point, building the A/B comparison harness becomes worthwhile, and a fresh ADR documents how the router decides between strategies.
- **Hungarian-language model leapfrog.** A model with materially better Hungarian KF-domain accuracy ships from any vendor; revisit on quality grounds even if all other criteria still hold.
