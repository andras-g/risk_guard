# Story 10.3: AI Batch Classifier — Full Packaging Stack Endpoint

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Hungarian KKV manufacturer or importer (SME_ADMIN) or the accountant acting on their behalf**,
I want **a new backend endpoint that accepts a batch of up to 100 `(VTSZ + description)` invoice-line pairs and returns the full 1–3-layer packaging stack per pair in one request**,
so that Story 10.4's tenant-onboarding flow can populate the Registry from 3 months of NAV invoices in minutes instead of hours — with per-pair Gemini classification, VTSZ-prefix fallback, and hard monthly-cap enforcement riding on the same usage-tracking backbone that Story 9.3 shipped for the single-pair path.

## Business Context

Story 9.3 shipped the **single-pair** KF-code classifier (`POST /api/v1/registry/classify`) used by the Registry editor's "Suggest" button and by Story 9.2's row-by-row triage bootstrap. That endpoint is synchronous, costs one Gemini call per invocation, and was never intended for bulk work — Story 9.2's triage UI deliberately required one "Approve" click per row, so the serial pattern was acceptable.

Epic 10 inverts the model. Story 10.4's new **invoice-driven bootstrap** fetches the last 3 months of NAV invoices (~3000 invoices × ~5 lines × ~30 % dedup → ~1000 unique `(vtsz, description)` pairs), classifies them in bulk, and populates the Registry directly — no user-facing triage queue. At 1000 pairs × one serial Gemini call each (~2 s latency + circuit-breaker wait), the serial path takes ~35 minutes; the user abandons the flow.

**Story 10.3 is the batch backbone.** One request, 1–100 pairs, bounded concurrency, per-pair failure isolation, monthly cap enforced, same routing as single-pair (Gemini → VTSZ-prefix fallback → UNRESOLVED). Each pair returns a full **1–3-layer packaging chain** (primary + optional secondary + optional tertiary) so Story 10.4 can populate `products_components` rows with multi-layer structure in one shot. The endpoint is stateless — it does **not** persist products, components, or Registry rows; it is a pure classification service that Story 10.4 consumes.

**Paradigm anchoring.** Per ADR-0003, `AuditService` is the single write path to audit tables. Story 10.3's classifier does not emit `registry_entry_audit_log` rows (there is no `productId` yet — that row lives in Story 10.4 when the classifier result is persisted). Instead, Story 10.3 records **observability counters** (`classifier.batch.pairs{strategy=...}`) via `MeterRegistry` — audit-grade compliance rows remain Story 10.4's job at persist time with `source=AI_SUGGESTED_CONFIRMED` (ADR-0003 §"Applied across Stories 10.1–10.9", line 81). This split is explicit and deliberate — see Dev Notes §"Two audit layers" below.

**Why a new endpoint, not an overload of `/registry/classify`.** The single-pair endpoint's response DTO (`ClassifyResponse`) wraps *one* `ClassificationResult` with one `strategy` field. A batch would require either (a) returning a list and dropping the per-request `strategy` field, breaking the existing contract, or (b) wrapping each result in a new envelope. Option (b) is the cleaner design and — since the batch has per-pair-independent strategies anyway (one pair may be GEMINI, another may be VTSZ_PREFIX_FALLBACK in the same batch) — the new endpoint lives at a new path with its own DTO shape. The single-pair endpoint continues to serve the Registry row-level "Suggest" button unmodified.

## Acceptance Criteria

> **Retro action T1 enforcement:** Task 1 below is the AC-to-task translation walkthrough — every AC here MUST have a matching task in the "Tasks / Subtasks" section. Do not open Task 2 until Task 1's walkthrough is filed in the Dev Agent Record. Story 10.1 and 10.2 enforced this and paid 0 AC-gap patches; Story 9.4 skipped it and paid 25+. Enforce it again.

### Part A — Endpoint contract

1. **New endpoint `POST /api/v1/classifier/batch-packaging`** returns `200 OK` on the happy path with a list of per-pair results. Sits at a new path — NOT on `/api/v1/registry/classify`. Controller class: new `BatchPackagingClassifierController` in `hu.riskguard.epr.registry.api` (same package as `RegistryClassifyController`).

2. **Request validation.** Request body shape:
   ```json
   { "pairs": [ { "vtsz": "39233000", "description": "PET palack 0,5L" }, … ] }
   ```
   - `pairs` field: `@NotNull`, `@Size(min = 1, max = 100)`, `@Valid`.
   - Each pair's `vtsz`: `@NotBlank`, `@Pattern(regexp = "^[0-9]{4,8}$")` (matches Story 9.3 VTSZ validation; tolerant of 4–8 digit codes).
   - Each pair's `description`: `@NotBlank`, `@Size(max = 500)`.
   - `pairs.size() < 1` → `400 Bad Request` with message identifying the field.
   - `pairs.size() > 100` → `400 Bad Request`.
   - Invalid VTSZ or blank description in any pair → `400 Bad Request` with indexed error detail (e.g., `pairs[3].vtsz: must match "^[0-9]{4,8}$"`).

3. **Response DTO.** New records in `hu.riskguard.epr.registry.api.dto`:
   - `BatchPackagingResponse(List<BatchPackagingResult> results, ClassifierUsageInfo usageInfo)`.
   - `BatchPackagingResult(String vtsz, String description, List<PackagingLayerDto> layers, String classificationStrategy, String modelVersion)`.
     - `classificationStrategy ∈ {"GEMINI", "VTSZ_PREFIX_FALLBACK", "UNRESOLVED"}` — string, not enum, to keep DTO stable across future strategies.
     - `layers: []` when strategy is `UNRESOLVED`.
   - `PackagingLayerDto(int level, String kfCode, BigDecimal weightEstimateKg, int itemsPerParent, String description)`.
     - `level ∈ {1, 2, 3}` (1=primary, 2=secondary, 3=tertiary — matches `products_components.wrapping_level` from Story 10.1 V20260417_001 migration).
     - `kfCode`: 8-digit string matching `^\d{8}$`.
     - `weightEstimateKg`: `BigDecimal`, strictly `> 0` and `≤ 10000` (T3 bound).
     - `itemsPerParent`: int, strictly `> 0` and `≤ 10000` (T3 bound).
     - `description`: Hungarian material name from the taxonomy (never blank).
   - `ClassifierUsageInfo(int callsUsedThisMonth, int callsRemaining, int monthlyCap)`.
   - Every response record carries a `static from(...)` factory per project convention (e.g., `BatchPackagingResponse.from(List<BatchPackagingResult>, ClassifierUsageInfo)`, `BatchPackagingResult.from(BatchClassifierService.PairResult)`, `PackagingLayerDto.from(KfSuggestion)`).

4. **Pair-to-layer mapping.** The per-pair packaging chain comes from the existing `ClassificationResult.suggestions()` list (already multi-layer per Story 9.6). Map `KfSuggestion.layer ∈ {"primary", "secondary", "tertiary"}` → `PackagingLayerDto.level ∈ {1, 2, 3}`. Map `KfSuggestion.unitsPerProduct` → `PackagingLayerDto.itemsPerParent` (they carry the same semantic per `products_components.items_per_parent` from Story 10.1 migration). Map `KfSuggestion.description` → `PackagingLayerDto.description`. Ordering: sort by `level` ascending (1 → 2 → 3) — matches `VertexAiGeminiClassifier.parseResponse()` line 264 which already sorts by `layerToOrder`.

5. **Per-pair failure isolation.** One Gemini failure (timeout, parse error, HTTP 5xx, circuit-breaker open) in a 100-pair batch MUST NOT abort the batch. That pair's result is `{ layers: [], classificationStrategy: "UNRESOLVED", modelVersion: null }`; other pairs return their normal result. This already matches the existing `VertexAiGeminiClassifier` behaviour (lines 92-96, 127-130 return `ClassificationResult.empty()` on any exception) — Story 10.3 must preserve that boundary per pair, NOT promote a single failure to a batch-level 500.

6. **Tier and role gating.** Controller class annotated `@TierRequired(Tier.PRO_EPR)` (matches `RegistryClassifyController:29`). Role gate inside the handler using `JwtUtil.requireRole(jwt, "batch classification requires SME_ADMIN, ACCOUNTANT, or PLATFORM_ADMIN", "SME_ADMIN", "ACCOUNTANT", "PLATFORM_ADMIN")`. Tenant ID MUST come from the JWT `active_tenant_id` claim via `JwtUtil.requireUuidClaim(jwt, "active_tenant_id")`; NEVER read from the request body. (Story 9.4 retrospective confirmed: tax-number / tenant-ID ownership checks must be strict equality against the JWT-bound tenant.)

### Part B — Monthly-cap enforcement (per-pair semantics)

7. **Cap counted per pair, not per request.** A 100-pair batch increments `AI_CLASSIFIER_USAGE.call_count` by exactly 100 when all pairs go through Gemini. Pairs that fall through to VTSZ-prefix fallback do NOT increment the counter (matches `ClassifierRouter:69` — increment happens only on successful Gemini result above confidence threshold).

8. **Batch rejection when remaining cap is insufficient.** Before any Gemini call fires, compute `callsRemaining = monthlyCap - callsUsedThisMonth`. If `callsRemaining < pairs.size()`, reject the entire batch with `HTTP 429 Too Many Requests`:
   - `Retry-After` header: seconds until the next calendar month boundary in Europe/Budapest (matches `ClassifierUsageService:24` BUDAPEST zone).
   - Response body: `ClassifierUsageInfo { callsUsedThisMonth, callsRemaining, monthlyCap }`.
   - Message: `"Monthly classifier cap would be exceeded: {pairs.size()} pairs requested, {callsRemaining} remaining."`.
   - Semantics: partial consumption is **all-or-nothing** — do NOT allow the first N pairs to consume cap while the remaining (N+1..end) fall through to fallback. The user expects either full classification or a clean rejection to retry later.

9. **Cap pre-check is best-effort, not atomic.** A concurrent batch from the same tenant could race through the pre-check. That is acceptable — the cap is a soft budget, and a single over-shoot by one batch is preferable to serialising all batches through a distributed lock. The circuit breaker and per-tenant concurrency limit (AC #15) bound the damage. Document this trade-off inline; do NOT introduce pessimistic locking for the cap check.

10. **`ClassifierUsageInfo` always returned.** Both the `200 OK` happy path and the `429` rejection include `ClassifierUsageInfo` so the UI (Story 10.4's bootstrap dialog) can show the user their remaining cap without a second roundtrip.

### Part C — Bounded concurrency + rate limiting

11. **Bounded concurrency for per-pair Gemini calls.** Default `10` concurrent Gemini calls per batch, configurable via `risk-guard.classifier.batch.concurrency` (int, default 10) in `application.yml`. Implementation uses `java.util.concurrent.StructuredTaskScope` on Java 25 virtual threads (matches `CompanyDataAggregator.java:59-88` pattern) with a `Semaphore(concurrency)` gate around each forked task — NOT a plain fixed thread pool, because the existing codebase uses structured concurrency for parallel external calls.

12. **Tenant context propagation across forked tasks.** Each forked task MUST run with the caller's `TenantContext` active. The `ClassifierRouter.classify(...)` reads `TenantContext.getCurrentTenant()` (line 56). Follow `CompanyDataAggregator.java:53,67` pattern: capture `tenantId` before forking, then re-establish context inside each task via `withTenant(tenantId, () -> classifierService.classify(...))`. A forked task without tenant context silently degrades (tenant=null → cap check returns false → Gemini call proceeds with ambiguous usage attribution).

13. **Load-test target.** With mocked 2-second per-pair Gemini latency and `concurrency=10`, a 20-pair batch completes in ≈ 8 seconds (two concurrent waves of 10 × 2 s). NOT 40 seconds (serial) and NOT ~2 seconds (unbounded parallel — would saturate the circuit breaker's `slidingWindowSize: 10` per application.yml:103). Verified by `BatchPackagingClassifierServiceLoadTest` with Mockito.when(...).thenAnswer(delayed(2 s)).

14. **Circuit breaker is shared.** All pairs in the batch route through the same `vertex-gemini` circuit breaker instance (`VertexAiGeminiClassifier:76`). If the breaker opens mid-batch, remaining pairs degrade to `UNRESOLVED` — matches AC #5 isolation, and is already the behaviour of `VertexAiGeminiClassifier.classify()` lines 92-96.

15. **Per-tenant concurrent-batch gate.** Max **3 concurrent batch requests per tenant**. Implemented as a `ConcurrentHashMap<UUID, Semaphore>` keyed by tenant (permits=3) in a small `BatchPackagingConcurrencyGate` component. Attempting a 4th concurrent batch returns `429 Too Many Requests` with body message `"Concurrent batch limit (3) exceeded for tenant"` and `Retry-After: 5` seconds (a rough hint — no external coordinator needed). Gate `acquire()` is non-blocking (`tryAcquire()`); `release()` runs in a `try/finally` around the batch execution.

### Part D — Prompt template + defensive bounds

16. **New prompt template `prompts/packaging-stack-v1.txt`** extending the existing single-pair prompt. Content requirements:
    - Same rules as `kf-classifier-system-prompt.txt` (primary/secondary/tertiary layers, weight bounds, JSON-only output, Hungarian material names).
    - Extra instruction: "This is a batch classification — each request contains exactly one `(vtsz, description)` pair; focus on the most likely primary + optional secondary + optional tertiary. Do NOT return more than 3 layers."
    - Few-shot examples seeded from 3 representative pairs in `DemoInvoiceFixtures` (PET bottle, cardboard box, aluminium can — the existing three fixtures at `DemoInvoiceFixtures.java` line items visible in seed data).
    - Taxonomy excerpt (`prompts/kf-taxonomy-excerpt.txt`) appended unchanged — same pattern as `VertexAiGeminiClassifier:82-86`.
    - File lives in `backend/src/main/resources/prompts/packaging-stack-v1.txt` and is loaded via `ClassPathResource` in the batch classifier's constructor.

17. **T3 defensive bounds on AI-returned numeric values** — before each layer is included in the response:
    - `weightEstimateKg`: strictly `> 0` AND `≤ 10000` — violation drops the layer with `log.warn("layer dropped: weight {} out of (0, 10000] for vtsz={}", ...)`.
    - `itemsPerParent`: strictly `> 0` AND `≤ 10000` — same drop behaviour.
    - `level`: must be in `{1, 2, 3}` — violation drops the layer.
    - `kfCode`: must match `^\d{8}$` — violation drops the layer.
    - More than 3 layers returned from Gemini → truncate to the first 3 by level ascending (1 → 2 → 3); if duplicates of the same level exist, keep the first occurrence per level.
    - If all layers for a pair get dropped, the pair's result becomes `UNRESOLVED` (same as if Gemini returned empty).

18. **`BigDecimal` construction from AI numeric values MUST use `new BigDecimal(jsonNode.asText())` — NEVER `BigDecimal.valueOf(asDouble())`.** This is the retro T3 lesson (Story 9.3 R2-P2) and is already the pattern at `VertexAiGeminiClassifier.java:235`. Reuse that approach. An ArchUnit rule is NOT added in this story (Story 10.5 will add the `hu.riskguard.epr.aggregation.*` scoped rule per Epic 10 cross-cutting constraint T3); but the convention is enforced by code review here.

### Part E — Observability + audit layer split

19. **Micrometer counter `classifier.batch.pairs`** tagged by `strategy ∈ {GEMINI, VTSZ_PREFIX_FALLBACK, UNRESOLVED}` and incremented once per pair processed. Registered once at startup per tag via the existing `MeterRegistry` injection pattern (`AuditService.java:44-53` is the template). Counter name chosen to not collide with the existing `audit.writes{source=...}` counter in `AuditService`.

20. **Micrometer timer `classifier.batch.duration`** recording the total batch-request duration (from controller entry to response write). Tagged by `pairCount` bucket (`1-10`, `11-50`, `51-100`). Standard `Timer.Sample` pattern.

21. **No `registry_entry_audit_log` rows emitted by Story 10.3.** The classifier is stateless — it does not persist products or components and therefore has no `productId` to key an audit row on. `FieldChangeEvent.productId` is non-null per `FieldChangeEvent.java:42`, so a productId-less audit row cannot exist. Audit rows for AI classifications land in Story 10.4 when the classifier result is persisted into `products_components`, via `AuditService.recordRegistryFieldChange(new FieldChangeEvent(productId, tenantId, "components[<id>].kf_code", null, newKfCode, userId, AuditSource.AI_SUGGESTED_CONFIRMED, "VERTEX_GEMINI", modelVersion))` — ADR-0003 §"Applied across Stories 10.1–10.9" line 81. This AC ratifies the split — see Dev Notes §"Two audit layers".

22. **PLATFORM_ADMIN usage dashboard reflects batch calls.** The existing `GET /api/v1/admin/classifier/usage` endpoint (at `EprAdminController.java:80-86`, gated to `PLATFORM_ADMIN` only — *not* the `/admin/ai-usage` path the epic skeleton hinted at) reads from `AI_CLASSIFIER_USAGE.call_count`. Since AC #7 ensures batch pairs increment the same counter one-at-a-time (via the same `ClassifierUsageService.incrementUsage(...)` call inside `ClassifierRouter:69`), no dashboard change is required. **Verified by manual test:** run a batch of 5 pairs → check dashboard shows `callsUsedThisMonth += 5` for the tenant. Document this in Completion Notes.

### Part F — Fallback semantics

23. **Fallback to VTSZ-prefix when Gemini empty/errors.** Per pair, if `ClassifierRouter.classify(...)` returns `ClassificationStrategy.VTSZ_PREFIX`, the pair's `classificationStrategy = "VTSZ_PREFIX_FALLBACK"`, `modelVersion = null` (matches `VtszPrefixFallbackClassifier:71` which produces rule-based results with null model version). Pair's `layers` list contains at most 1 layer (primary only — the VTSZ-prefix fallback produces single-layer results per `VtszPrefixFallbackClassifier:72`).

24. **`ClassificationStrategy.NONE` maps to `"UNRESOLVED"`** in the response DTO. Layers list is empty.

### Part G — Tests

25. **Unit tests — `BatchPackagingClassifierServiceTest`** (backend, Mockito, no Spring context):
    - (a) 10 representative Hungarian category pairs → stable multi-layer output via mocked `ClassifierRouter.classify(...)`. Golden fixtures in `src/test/resources/golden/batch-packaging-v1.json` (PET bottle, cardboard box, aluminium can, glass jar, EPS tray, metal can, paper bag, plastic film, wooden pallet, shrink wrap — all matching existing `DemoInvoiceFixtures` line items).
    - (b) Per-pair failure isolation: one `RuntimeException` thrown by the mocked classifier for pair #3 in a 20-pair batch; assert the other 19 return normally and pair #3 returns `UNRESOLVED` with empty layers.
    - (c) T3 bound violations — 6 dedicated tests, one per violation class:
      - `weightEstimateKg = 0` → layer dropped.
      - `weightEstimateKg = 10001` → layer dropped.
      - `itemsPerParent = 0` → layer dropped.
      - `itemsPerParent = 10001` → layer dropped.
      - `level = 4` → layer dropped.
      - `kfCode = "123"` (not 8 digits) → layer dropped.
      - All-layers-dropped → pair becomes `UNRESOLVED`.
      - 4-layer Gemini response → truncated to first 3 by level.
      - Empty Gemini response → `UNRESOLVED`.
    - (d) Fallback tagging: pair routed via `VTSZ_PREFIX` → `classificationStrategy = "VTSZ_PREFIX_FALLBACK"`, `modelVersion = null`.
    - (e) Concurrency honoured: `@Test` with a `CountDownLatch`-based mock that verifies at most `concurrency` (10) calls are in-flight simultaneously when 20 pairs submit — uses `StructuredTaskScope` timing assertion as a load-test proxy.

26. **Controller test — `BatchPackagingClassifierControllerTest`** (Spring `@WebMvcTest` + Mockito, matches `RegistryClassifyControllerTest` pattern):
    - (a) Happy path: valid 3-pair batch returns `200 OK` with `BatchPackagingResponse` + `ClassifierUsageInfo`.
    - (b) OpenAPI/request validation — `pairs=[]` → `400`; `pairs.size()=101` → `400`; pair with invalid VTSZ → `400` with indexed error detail; pair with blank description → `400`.
    - (c) Missing `active_tenant_id` JWT claim → `401` (reuses `JwtUtil.requireUuidClaim` contract).
    - (d) Role gating: `GUEST` / `SME_USER` role → `403`; `SME_ADMIN` / `ACCOUNTANT` / `PLATFORM_ADMIN` → `200`.
    - (e) `@TierRequired(Tier.PRO_EPR)` present on the controller class — reflection assertion.
    - (f) Cap exceeded → `429` with `Retry-After` header set + `ClassifierUsageInfo` in body.
    - (g) Concurrent-batch gate: mocked `BatchPackagingConcurrencyGate` at permit=0 → `429` with body `"Concurrent batch limit (3) exceeded"`.

27. **Integration test — `BatchPackagingClassifierIntegrationTest`** (gated by `RG_INTEGRATION_VERTEX_AI=true`, matches `VertexAiGeminiClassifierIntegrationTest:25` pattern). Three real Hungarian pairs against live Vertex AI; assert KF-code family prefixes (1101 for plastics, 1105 for glass, 1106 for metals). Skips when env var absent — CI configures the flag only on the `integration-vertex` job.

28. **ArchUnit** — no new rule in this story. Verified that existing `EpicTenInvariantsTest` (`only_audit_package_writes_to_audit_tables`, `audit_service_is_the_facade`) still passes; the batch classifier does not write to audit tables (AC #21), so the rules are unaffected.

### Part H — Regression safety

29. **Single-pair classifier endpoint unchanged.** `POST /api/v1/registry/classify` (Story 9.3, `RegistryClassifyController:27`) continues to serve the Registry row-level "Suggest" button. Do NOT modify `RegistryClassifyController`, `ClassifyRequest`, `ClassifyResponse`, `KfSuggestionDto`, or the single-pair endpoint's behaviour in any way. `RegistryClassifyControllerTest` stays green without modification.

30. **`ClassifierRouter`, `VertexAiGeminiClassifier`, `VtszPrefixFallbackClassifier`, `ClassifierUsageService`, `ClassifierUsageRepository` unchanged.** Story 10.3 composes the existing `ClassifierRouter` interface; it does not add new classifier implementations nor change the routing logic. `ClassifierRouterTest`, `VtszPrefixFallbackClassifierTest`, `ClassifierUsageServiceTest` stay green unmodified.

31. **Existing `prompts/kf-classifier-system-prompt.txt` unchanged.** Story 10.3 adds a new prompt file `prompts/packaging-stack-v1.txt` — the existing system prompt continues to serve the single-pair endpoint.

32. **No schema / Flyway migration in this story.** Story 10.1 already added all Epic 10 columns (`wrapping_level`, `items_per_parent`, `material_template_id`) to `products_components`. Story 10.3 is a pure compute/endpoint story — no new tables, no new columns.

33. **`tech-radar.md` updated per T5.** The Epic 10 cross-cutting constraint T5 requires Spring AI / GCP SDK compatibility matrix update in `tech-radar.md` before Story 10.3. **Current reality: `docs/architecture/tech-radar.md` does not exist.** Task 2 of this story is to create the file (minimal content acceptable — Spring AI versions already pinned in `application.yml:28-36` and `backend/build.gradle`; new task is just to consolidate them into a single doc for future audit). If the reviewer deems the consolidation out of scope, task may be descoped with documented rationale in Completion Notes — the classifier itself does not gain new SDK dependencies in this story (only new HTTP endpoint + DTOs).

### Part I — Retro discipline + verification

34. **AC-to-task walkthrough (T1) filed in the Dev Agent Record before any code task starts.** See Task 1 below.

35. **Full suite green.**
    - Targeted: `./gradlew test --tests "hu.riskguard.epr.*"` ~90 s green.
    - ArchUnit: `./gradlew test --tests "hu.riskguard.architecture.*"` ~30 s green.
    - Full backend ONCE at end: `./gradlew test` green (expected test count ≥ 903 + new tests from this story).
    - Frontend: `cd frontend && npm run test -- --run` ~6 s — **no frontend changes in this story**, count stays at 796. Run anyway to confirm zero regression.
    - Contract: `cd frontend && npx tsc --noEmit` — 0 errors (no frontend changes, but verify).
    - Lint: `cd frontend && npm run lint` — 0 errors.
    - i18n: `npm --prefix frontend run lint:i18n` — `22 files OK — keys alphabetical at every level` (no new keys in this story, but verify nothing regressed).
    - Playwright E2E: 5 scenarios green. No new E2E scenario in this story (the endpoint's consumer is Story 10.4's bootstrap flow; E2E coverage lands there).
    - **Never pipe `gradlew`** (user memory). Run raw.

## Tasks / Subtasks

> **Order matters.** Task 1 (AC-to-task walkthrough) is a GATE — do not open any other task until it is filed. Tasks 2–9 can be worked in one branch but should be committed in order so each commit compiles and tests green. Task 10 (full-suite verification) is last.

- [x] **Task 1 — AC-to-task walkthrough (retro T1 GATE)** (AC: #34)
  - [x] Before writing a single line of production code, read every AC above once. For each AC, list below its number plus the task number(s) that cover it. File the walkthrough verbatim in the Dev Agent Record's "Completion Notes List" with heading `### AC-to-Task Walkthrough (T1)`.
  - [x] Any AC without a matching task → add a task in this section **before proceeding**. Do NOT skip this step; Story 9.4 paid 25+ patches for exactly this omission.

- [x] **Task 2 — `tech-radar.md` consolidation (retro T5)** (AC: #33)
  - [x] Create `docs/architecture/tech-radar.md` if it does not exist. Minimal viable content:
    - Section "AI / Classifier" — pin Spring AI Vertex Gemini versions (from `application.yml:28-36`): `gemini-3.0-flash-preview`, location `europe-west1`, project-id env-var `GCP_PROJECT_ID`.
    - Section "GCP SDKs" — `google-auth-library-oauth2-http` version from `backend/build.gradle` dependency line (grep it).
    - Section "Resilience4j" — `vertex-gemini` circuit-breaker config reference (`application.yml:102-107`).
    - Section "Java runtime" — Java 25, `StructuredTaskScope` as the concurrency primitive (beta API per JEP 462).
    - Front-matter: date = today (2026-04-19), author = SM/dev, status = Living.
  - [x] If the reviewer in R1 decides this consolidation is out of scope, document the decision in Completion Notes with a pointer to `application.yml:28-36` as the de-facto source — no additional code impact.

- [x] **Task 3 — Configuration: `classifier.batch.concurrency` + YAML** (AC: #11)
  - [x] Edit `backend/src/main/resources/application.yml` — add under the existing `risk-guard.classifier:` block (line 161):
    ```yaml
    risk-guard:
      classifier:
        confidence-threshold: MEDIUM
        monthly-cap: 1000
        batch:
          concurrency: 10          # max concurrent Gemini calls per batch
    ```
  - [x] Verify `@Value("${risk-guard.classifier.batch.concurrency:10}")` injection works in a local smoke test (the batch classifier constructor, Task 5).

- [x] **Task 4 — DTOs in `hu.riskguard.epr.registry.api.dto`** (AC: #2, #3, #4, #10)
  - [x] Create `BatchPackagingRequest.java`:
    ```java
    public record BatchPackagingRequest(
        @NotNull @Size(min = 1, max = 100) @Valid List<PairRequest> pairs
    ) {
        public record PairRequest(
            @NotBlank @Pattern(regexp = "^[0-9]{4,8}$") String vtsz,
            @NotBlank @Size(max = 500) String description
        ) {}
    }
    ```
  - [x] Create `BatchPackagingResponse.java` with fields `List<BatchPackagingResult> results`, `ClassifierUsageInfo usageInfo` and a `static from(List<BatchPackagingResult>, ClassifierUsageInfo)` factory.
  - [x] Create `BatchPackagingResult.java` with fields `String vtsz, String description, List<PackagingLayerDto> layers, String classificationStrategy, String modelVersion` and a `static from(String vtsz, String description, ClassificationResult result)` factory that performs the enum → string mapping (see AC #23, #24).
  - [x] Create `PackagingLayerDto.java` with fields `int level, String kfCode, BigDecimal weightEstimateKg, int itemsPerParent, String description` and a `static from(KfSuggestion suggestion)` factory that converts `layer: "primary"→1 / "secondary"→2 / "tertiary"→3` and `unitsPerProduct→itemsPerParent`. Reject-by-returning-null if any T3 bound is violated (caller filters nulls out per Task 5).
  - [x] Create `ClassifierUsageInfo.java` with fields `int callsUsedThisMonth, int callsRemaining, int monthlyCap` and a `static of(int used, int cap)` factory that computes `callsRemaining = Math.max(0, cap - used)`.
  - [x] All records in `api.dto` package; all carry `static from(...)` factories per project convention (matches Story 9.3 `KfSuggestionDto.from(KfSuggestion)` pattern).

- [x] **Task 5 — `BatchPackagingClassifierService` (domain service)** (AC: #4, #5, #7, #11, #12, #14, #17, #18, #19, #20, #23, #24)
  - [x] New class `hu.riskguard.epr.registry.domain.BatchPackagingClassifierService` (in `domain` package because it orchestrates the domain-level classifier — matches `ClassifierUsageService` placement; controller is in `api`).
  - [x] Constructor injects: `KfCodeClassifierService` (routed to `ClassifierRouter` via `@Primary`), `ClassifierUsageService`, `MeterRegistry`, `@Value("${risk-guard.classifier.batch.concurrency:10}")` int concurrency.
  - [x] Public method `classify(List<PairRequest> pairs, UUID tenantId)` returns `List<BatchPackagingResult>`:
    - Uses `StructuredTaskScope.open()` with `Joiner.awaitAll()` (Java 25; matches `CompanyDataAggregator.java:59-88`).
    - Fork one virtual-thread task per pair, each task:
      - Acquires a permit from a `Semaphore(concurrency)` shared across the batch (before calling the classifier).
      - Re-establishes `TenantContext` via `withTenant(tenantId, () -> classifierService.classify(description, vtsz))` (AC #12).
      - Maps `ClassificationResult` → `BatchPackagingResult` via `BatchPackagingResult.from(vtsz, description, result)`, applying T3 bound filtering (AC #17): drop any layer with bounds violations; if all layers dropped and strategy was GEMINI, promote the pair to `UNRESOLVED`.
      - Catches any `Exception` inside the task and returns a `BatchPackagingResult` with `UNRESOLVED` strategy + empty layers (AC #5). Log at WARN with pair identifier.
      - Releases the permit in `finally`.
    - Returns results in the same order as input pairs (preserve order via indexed `AtomicReferenceArray<BatchPackagingResult>` keyed by index, then `Arrays.asList`).
  - [x] Increment Micrometer counters (AC #19): `classifier.batch.pairs{strategy=GEMINI|VTSZ_PREFIX_FALLBACK|UNRESOLVED}` once per pair. Counter registry initialised once in constructor per tag, stored in a `Map<String, Counter>`.
  - [x] Time each batch via `Timer.Sample` tagged with `pairCount` bucket (AC #20).
  - [x] Class is `@Service`. NOT `@Transactional` at class level (the classifier writes nothing to DB; `ClassifierUsageService.incrementUsage(...)` owns its own `@Transactional` per `ClassifierUsageService:47`).
  - [x] Helper `withTenant(UUID, Supplier<T>)` — if not already in a shared utility class, create a small private static helper in this class. Check `hu.riskguard.core.security.TenantContext` for existing wrap helpers first; if none, adapt `CompanyDataAggregator.java`'s inline pattern.

- [x] **Task 6 — Cap pre-check + `BatchPackagingConcurrencyGate`** (AC: #6, #7, #8, #9, #10, #15)
  - [x] New class `hu.riskguard.epr.registry.domain.BatchPackagingConcurrencyGate` — `@Component`, thread-safe, holds `ConcurrentHashMap<UUID, Semaphore>` with `computeIfAbsent(tenantId, k -> new Semaphore(3))`. Public methods: `boolean tryAcquire(UUID tenantId)` (non-blocking), `void release(UUID tenantId)`. Do NOT leak `Semaphore` object — only expose acquire/release.
  - [x] New class `hu.riskguard.epr.registry.api.exception.ClassifierCapExceededException extends RuntimeException` — carries `ClassifierUsageInfo` payload; controller maps this to `429` with the usage-info body + `Retry-After` header (seconds to next Europe/Budapest month boundary).
  - [x] New class `hu.riskguard.epr.registry.api.exception.BatchConcurrencyLimitExceededException extends RuntimeException` — controller maps this to `429` with body `"Concurrent batch limit (3) exceeded for tenant"` + `Retry-After: 5`.
  - [x] Controller exception handler in the same controller class (or a shared `@RestControllerAdvice` if one already exists in `hu.riskguard.epr.*` — grep first). Handler methods return `ResponseEntity<ClassifierUsageInfo>` / `ResponseEntity<ErrorResponse>` with appropriate status + headers. (Implemented in Task 7's controller class via `@ExceptionHandler`.)

- [x] **Task 7 — `BatchPackagingClassifierController`** (AC: #1, #2, #5, #6, #8, #10, #15)
  - [x] New class `hu.riskguard.epr.registry.api.BatchPackagingClassifierController`:
    ```java
    @RestController
    @RequestMapping("/api/v1/classifier/batch-packaging")
    @RequiredArgsConstructor
    @TierRequired(Tier.PRO_EPR)
    public class BatchPackagingClassifierController {
        private final BatchPackagingClassifierService batchService;
        private final ClassifierUsageService usageService;
        private final BatchPackagingConcurrencyGate concurrencyGate;
        private final int monthlyCap;  // @Value("${risk-guard.classifier.monthly-cap:1000}")

        @PostMapping
        public BatchPackagingResponse classify(
                @Valid @RequestBody BatchPackagingRequest request,
                @AuthenticationPrincipal Jwt jwt) {
            JwtUtil.requireRole(jwt, "batch classification requires SME_ADMIN, ACCOUNTANT, or PLATFORM_ADMIN",
                    "SME_ADMIN", "ACCOUNTANT", "PLATFORM_ADMIN");
            UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");

            // Concurrent-batch gate (AC #15)
            if (!concurrencyGate.tryAcquire(tenantId)) {
                throw new BatchConcurrencyLimitExceededException();
            }
            try {
                // Cap pre-check (AC #8)
                int used = usageService.getCurrentMonthCallCount(tenantId);  // (new helper — see Task 6)
                int remaining = Math.max(0, monthlyCap - used);
                if (remaining < request.pairs().size()) {
                    throw new ClassifierCapExceededException(
                            ClassifierUsageInfo.of(used, monthlyCap));
                }

                List<BatchPackagingResult> results = batchService.classify(request.pairs(), tenantId);

                // Re-read usage after batch to compute post-batch remaining (AC #10)
                int usedAfter = usageService.getCurrentMonthCallCount(tenantId);
                return BatchPackagingResponse.from(results, ClassifierUsageInfo.of(usedAfter, monthlyCap));
            } finally {
                concurrencyGate.release(tenantId);
            }
        }
    }
    ```
  - [x] Add `ClassifierUsageService.getCurrentMonthCallCount(UUID)` — new public method that reads `AI_CLASSIFIER_USAGE.call_count` for the current Europe/Budapest month (repository helper; returns 0 if row absent). This is the minimal extension needed; do NOT change the existing `isCapExceeded` / `incrementUsage` / `getAllTenantsUsage` signatures.
  - [x] Add `ClassifierUsageRepository.getCallCountForMonth(UUID, String yearMonth)` if not already present (it's not — `ClassifierUsageRepository.java:30-37` only exposes a boolean `isCapExceeded`). Extract the `CALL_COUNT` read into its own public method for reuse; `isCapExceeded` then becomes `getCallCountForMonth(...) >= cap`. Both code paths stay green.
  - [x] Exception handler methods in the controller (`@ExceptionHandler(ClassifierCapExceededException.class)` → `429 + Retry-After` + `ClassifierUsageInfo` body; `@ExceptionHandler(BatchConcurrencyLimitExceededException.class)` → `429 + Retry-After: 5`). Compute Europe/Budapest month-boundary seconds inline — `ZonedDateTime.now(ZoneId.of("Europe/Budapest")).plusMonths(1).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS)` minus `now`.

- [x] **Task 8 — Prompt template + batch classifier wiring** (AC: #16, #31)
  - [x] Create `backend/src/main/resources/prompts/packaging-stack-v1.txt`. Start from the existing single-pair prompt (`kf-classifier-system-prompt.txt`); append:
    - Few-shot block with 3 examples from `DemoInvoiceFixtures`: "PET palack 0,5L" (VTSZ 39233000), "Kartondoboz 40x30x20" (VTSZ 48191000), "Csavar M6x30" (VTSZ 73181500) — pull the exact descriptions from `DemoInvoiceFixtures.java` line-item constants.
    - Explicit instruction: "Return at most 3 layers. Prefer primary-only when secondary/tertiary are uncertain."
  - [x] Decide: does Story 10.3 route batch pairs through the existing `VertexAiGeminiClassifier` (reusing the single-pair prompt) or load the new `packaging-stack-v1.txt` prompt for batch calls? **Recommended path:** reuse the existing `ClassifierRouter` as the composition boundary — Story 10.3 does NOT modify `VertexAiGeminiClassifier` — and the new prompt template is a *documentation* artefact for Story 10.4's orchestration layer. Load the prompt file in `BatchPackagingClassifierService`'s constructor via `ClassPathResource` as a placeholder for future prompt swap (Story 10.5+ may want its own prompt); inject but do not (yet) pass it down. Document this decision in Completion Notes. **Alternative:** introduce a new classifier implementation with its own prompt — adds complexity and a new Spring bean; NOT chosen for 10.3 scope. **Decision: recommended path taken.** `BatchPackagingClassifierService` constructor loads `prompts/packaging-stack-v1.txt` via `ClassPathResource` (fail-fast at startup) but routing keeps `ClassifierRouter` unmodified per AC #29/#30/#31.
  - [x] Smoke test: load `packaging-stack-v1.txt` as a `ClassPathResource` in a unit test; assert content contains "packaging-stack", "primary", and at least one VTSZ example. (Lands in Task 9 as part of `BatchPackagingClassifierServiceTest`.)

- [x] **Task 9 — Tests** (AC: #25, #26, #27, #28)
  - [x] Create `backend/src/test/java/hu/riskguard/epr/registry/BatchPackagingClassifierServiceTest.java` — 10 golden-pair test + failure-isolation test + 6 T3-bound tests + 4-layer truncation test + empty-response test + fallback-tagging test + concurrency test (see AC #25 for full list). Use Mockito to stub `KfCodeClassifierService` and `ClassifierUsageService`; construct a real `SimpleMeterRegistry` for counter assertions.
  - [x] Golden fixture: create `backend/src/test/resources/golden/batch-packaging-v1.json` with 10 canonical pairs + expected mock `ClassificationResult` per pair. Structure: `[{"input":{"vtsz":"...","description":"..."},"mockResult":{"strategy":"VERTEX_GEMINI","suggestions":[...]}}]`. Load via `ObjectMapper` in the test setup.
  - [x] Create `backend/src/test/java/hu/riskguard/epr/registry/BatchPackagingClassifierControllerTest.java` — happy path + 7 validation/gating cases per AC #26. (Implementation note: matches the existing `RegistryClassifyControllerTest` Mockito-only style — direct controller construction rather than `@WebMvcTest` — because every other controller test in `hu.riskguard.epr.registry` follows that pattern.)
  - [x] Create `backend/src/test/java/hu/riskguard/epr/registry/classifier/BatchPackagingClassifierIntegrationTest.java` — mirror the `VertexAiGeminiClassifierIntegrationTest:25` env-gated pattern. Skips unless `RG_INTEGRATION_VERTEX_AI=true`. 3 real Hungarian pairs.
  - [x] Targeted run: `./gradlew test --tests "hu.riskguard.epr.registry.*"` — green. (Run as part of Task 10 verification.)

- [x] **Task 10 — Full suite + Dev Notes + File List** (AC: #29, #30, #32, #35)
  - [x] Targeted backend: `./gradlew test --tests "hu.riskguard.epr.*"` — green.
  - [x] ArchUnit: `./gradlew test --tests "hu.riskguard.architecture.*"` — green (0 regressions; no new rule in 10.3 per AC #28).
  - [x] Full backend ONCE at end: `./gradlew test` — green (BUILD SUCCESSFUL).
  - [x] Frontend smoke: `cd frontend && npm run test -- --run` — green (0 frontend changes; **797 tests passed**, up from 796 — count grew, not shrank, so no 10.3 regression).
  - [x] `cd frontend && npx tsc --noEmit` — 0 errors.
  - [x] `cd frontend && npm run lint` — 0 errors (warnings unchanged from baseline).
  - [x] `npm --prefix frontend run lint:i18n` — `22 files OK — keys alphabetical at every level`.
  - [ ] Playwright E2E: 5 scenarios green — no 10.3 scenario added (consumer is Story 10.4). **Not re-run locally — no backend endpoint used by existing E2E suites was modified; Story 9.3 single-pair endpoint, Registry CRUD, EPR filing, and OKIRkapu export paths are all untouched per AC #29–32.** Flag for R1 reviewer if regression proof is required.
  - [x] File Completion Notes List entries per task.
  - [x] Update File List with backend files created/modified.
  - [x] Flip story status to `review` by editing `sprint-status.yaml`.

### Review Findings

- [x] [Review][Patch] R1-P1: packaging-stack-v1.txt Pair 3 uses wrong VTSZ — spec AC #16 requires VTSZ `76129020` (aluminium can); prompt has `73181500` (Csavar M6x30). Fix: replace Pair 3 with the aluminium-can example. [`backend/src/main/resources/prompts/packaging-stack-v1.txt:65-71`]
- [x] [Review][Patch] R1-P2: `risk-guard.classifier.batch.per-tenant-concurrent` absent from application.yml — `BatchPackagingConcurrencyGate` is configurable via this key (default 3) but it is not declared in `application.yml` alongside `batch.concurrency: 10`, making it invisible to ops. AC #15 implies same discoverability as AC #11. [`backend/src/main/resources/application.yml:165`]
- [x] [Review][Patch] R1-P3: Missing T3 boundary-pass tests (AC #25c) — `weightEstimateKg = 10000` should pass (spec says `≤ 10000`); `itemsPerParent = 10000` should pass. Tests exist for violations (0 and 10001) but no test verifies the exact upper boundary is accepted, not rejected. [`BatchPackagingClassifierServiceTest.java`]
- [x] [Review][Patch] R1-P4: Missing multi-layer all-dropped test (AC #25c) — AC #25c explicitly requires "all-layers-dropped" as a named sub-test. Existing tests cover single-layer drop → UNRESOLVED, but no test exercises a 3-suggestion response where each suggestion fails a different T3 bound (e.g., bad weight, bad kfCode, bad level), verifying the `BatchPackagingResult.from()` line-71 UNRESOLVED promotion for multi-suggestion input. [`BatchPackagingClassifierServiceTest.java`]
- [x] [Review][Patch] R1-P5: Missing dedicated SME_ADMIN 200-pass role test (AC #26d) — AC #26d requires a targeted test for each of the three permitted roles. `classify_accountantRole_passes` and `classify_platformAdminRole_passes` exist; `classify_smeAdminRole_passes` does not (SME_ADMIN only appears as the role in the multi-concern happy-path test). [`BatchPackagingClassifierControllerTest.java`]
- [x] [Review][Patch] R1-P6: tech-radar.md "Spring AI NOT used" creates confusion — the AI/Classifier section says "Spring AI — NOT used as a wrapper" but does not explain why `spring.ai.vertex.ai.gemini.*` keys appear in `application.yml`. The radar should add a one-liner: "`spring.ai.*` config keys are present in application.yml from initial scaffolding; Spring AI is excluded from the runtime classpath (see `build.gradle:113-119`). The keys are dead config — harmless but misleading." (AC #33) [`docs/architecture/tech-radar.md:15`]
- [x] [Review][Patch] R1-P7: `BatchPackagingResult.from()` silently promotes to UNRESOLVED with no aggregate log — `PackagingLayerDto.from()` logs WARN per dropped layer, but when all layers are T3-dropped the caller (`BatchPackagingResult.from()` line 71) returns UNRESOLVED without logging. A `log.warn("all {} layers T3-dropped for vtsz={}, strategy={} → UNRESOLVED", ...) ` at that point is needed so dashboards can distinguish "Gemini returned garbage" from "Gemini returned nothing". [`BatchPackagingResult.java:71-74`]
- [x] [Review][Defer] D1: TOCTOU cap race — two concurrent batches from the same tenant can both pass the cap pre-check when remaining ≥ max(N1, N2) but N1+N2 > remaining. AC #9 explicitly accepts best-effort semantics; documented in Dev Notes. Bounded by per-tenant concurrency gate (max 3) + circuit breaker. — deferred, pre-existing design decision.
- [x] [Review][Defer] D2: `BatchPackagingConcurrencyGate` Semaphore map unbounded growth — one entry per tenant UUID, never evicted. Acceptable for bounded tenant count; documented in class Javadoc. — deferred, pre-existing design decision.
- [x] [Review][Defer] D3: `TenantContext.setCurrentTenant/clear` in virtual threads — correct for ThreadLocal-based `TenantContext`; follows `CompanyDataAggregator` pattern. ScopedValue path would require `ScopedValue.where(...).run(...)` but the codebase uses the ThreadLocal compatibility wrapper. — deferred, pre-existing pattern.
- [x] [Review][Defer] D4: `scope.join()` InterruptedException + `StructuredTaskScope.close()` interaction — with interrupt status set, `close()` cancels tasks and re-throws; propagates as undeclared checked exception through try-with-resources. Low probability in practice; Java 25 StructuredTaskScope contract handles cleanup. — deferred, pre-existing structural concern.
- [x] [Review][Defer] D5: Double DB roundtrip `usedBefore` + `usedAfter` — controller reads usage count twice to provide accurate post-batch snapshot. Second read is best-effort; concurrent batches from the same tenant can cause slight inaccuracy. By design (AC #9). — deferred, pre-existing design decision.
- [x] [Review][Defer] D6: DST edge case in `secondsUntilNextBudapestMonth()` — `truncatedTo(DAYS)` on CET/CEST never encounters a midnight DST gap. Safe in practice; `toLocalDate().atStartOfDay(BUDAPEST)` would be more correct but is low priority. — deferred, pre-existing.
- [x] [Review][Defer] D7: `isCapExceeded` refactored to `getCallCountForMonth() >= cap` changes `cap=0` semantics — old code: `count != null && count >= cap` = false when row absent; new code: `0 >= 0` = true. Only affects `monthly-cap: 0` config which is not a valid production value. — deferred, extreme edge case.
- [x] [Review][Defer] D8: AC #16 taxonomy excerpt not appended to `packaging-stack-v1.txt` — Decision notes explicitly defer routing this prompt to `VertexAiGeminiClassifier` until Story 10.5+. Taxonomy append should happen at that routing point, not in the scaffold. — deferred, Story 10.5 scope.
- [x] [Review][Defer] D9: AC #13 load-test timing assertion — concurrency test verifies in-flight count ≤ configured limit (correct) but does not assert wall-clock duration is between serial (~40s) and unbounded (~2s) as AC #13 specifies. Timing assertions are flaky in CI. Functional behaviour is verified. — deferred.
- [x] [Review][Defer] D10: Post-batch `usedAfter` may reflect other tenants' concurrent increments — by design (best-effort); the value is informational for the response, not a billing guarantee. — deferred, pre-existing design decision.
- [x] [Review][Defer] D11: `BatchPackagingResult.from()` returns UNRESOLVED for all-layers-dropped without preserving original strategy in counter — Micrometer shows UNRESOLVED when Gemini returned content that all failed T3 bounds, making it impossible to distinguish "model returned nothing" from "model returned invalid content". P7 adds a log; counter attribution is a follow-up observability improvement. — deferred.
- [x] [Review][Defer] D12: `BatchPackagingConcurrencyGate.release()` has no upper-bound guard — `Semaphore.release()` has no max-permit ceiling; a mis-use would inflate permits permanently. Only reachable from a correctly paired `finally` block in current code. — deferred, pre-existing structural concern.

### R2 Review Findings (2026-04-20)

R2 pass via parallel adversarial layers (Blind Hunter + Edge Case Hunter + Acceptance Auditor). 19 candidate findings; 11 patches, 4 new defers, 4 dismissed as duplicates of R1 D1/D2/D10 or unconfirmed.

- [x] [R2][Patch] R2-P1: `secondsUntilNextBudapestMonth()` returns 1 second on Jan 31 (and any last-day-of-month whose next month is shorter) — `plusMonths(1).withDayOfMonth(1).truncatedTo(DAYS)` resolves to Feb 1 of the **current** year, producing a negative `Duration`, clamped to `Math.max(1L, seconds) = 1`. Clients hit a thundering-herd cap-retry storm that day. Fix: navigate via `YearMonth.from(now).plusMonths(1).atDay(1).atStartOfDay(BUDAPEST)`. [`BatchPackagingClassifierController.java:122-130`]
- [x] [R2][Patch] R2-P2: `withTenant(tenantId, …)` unconditionally calls `TenantContext.clear()` in `finally` even when `tenantId == null` and `setCurrentTenant` was never invoked — wipes any pre-existing carrier-thread binding. Fix: track `set` flag and clear only when this method set the binding. [`BatchPackagingClassifierService.java:181-199`]
- [x] [R2][Patch] R2-P3: `strategyCounters.get(outcome.classificationStrategy()).increment()` NPEs silently inside the fork if an unknown strategy string is ever produced (e.g., future strategy enum, test stub); `StructuredTaskScope.Joiner.awaitAll()` swallows the NPE and the slot stays unfilled. Fix: route all increments through a single `incrementStrategyCounter(String)` helper that falls back to the UNRESOLVED counter + WARN log when no registered counter matches. [`BatchPackagingClassifierService.java:122, 142, 198-206`]
- [x] [R2][Patch] R2-P4: `PackagingLayerDto.from(KfSuggestion suggestion)` dereferences `suggestion.layer()` with no null-guard — a forgiving JSON parser returning a null element would NPE up the stack and collapse the whole pair to UNRESOLVED instead of dropping just that bad suggestion. Fix: null-guard at top of factory. [`PackagingLayerDto.java:48-55`]
- [x] [R2][Patch] R2-P5: `BatchPackagingConcurrencyGate.release()` called for a tenant whose semaphore was never created auto-creates a fresh `Semaphore(permits)` via `computeIfAbsent` and immediately `release()`s it, permanently inflating permits to `permits+1` for that tenant. Fix: `tenantSemaphores.get(...)` (not `computeIfAbsent`) and WARN-log when unpaired. [`BatchPackagingConcurrencyGate.java:46-58`]
- [x] [R2][Patch] R2-P6: Integration-test comment clarified — `BatchPackagingClassifierIntegrationTest` matches the precedent `VertexAiGeminiClassifierIntegrationTest:25` pattern (direct classifier, not `ClassifierRouter`), which is exactly what AC #27 asks for. Router-level fallback/cap behaviour is covered by unit tests (`ClassifierRouterTest`, `BatchPackagingClassifierServiceTest`). Strengthened the class-javadoc to make the intent explicit. [`BatchPackagingClassifierIntegrationTest.java:25-30`]
- [x] [R2][Patch] R2-P7: `classifier.batch.duration` Timer was built via `Timer.builder(...).register(meterRegistry)` **inside every `classify()` call** rather than once at startup — deviates from AC #19's constructor-registration pattern and the `AuditService:44-53` template that AC #20 inherits. Fix: pre-register one Timer per pairCount bucket (`1-10`, `11-50`, `51-100`) in the constructor; the per-call path looks them up. [`BatchPackagingClassifierService.java:77-82, 115-117`]
- [x] [R2][Patch] R2-P8: `ClassifierUsageInfo` exposed only `of(int used, int cap)` — deviates from AC #3 which mandates `static from(...)` on every response record. Fix: add `from(int used, int cap)` as the canonical factory; keep `of(...)` as a delegating alias so existing call sites compile unchanged. [`ClassifierUsageInfo.java:14-21`]
- [x] [R2][Patch] R2-P9: AC #8 specifies both `Response body: ClassifierUsageInfo` AND `Message: "Monthly classifier cap would be exceeded: {N} pairs requested, {M} remaining."` — the Message text only lived on the Java exception's `getMessage()` and was never visible to HTTP clients. Fix: surface it via an `X-Error-Message` response header so the body contract stays `ClassifierUsageInfo` per AC. [`BatchPackagingClassifierController.java:107-120`]
- [x] [R2][Patch] R2-P10: Concurrency test (`classify_concurrencyLimitHonoured_neverExceedsConfigured`) used `configuredConcurrency = 5` while AC #25(e) verbatim specifies "at most `concurrency` (10) calls are in-flight simultaneously when 20 pairs submit". Fix: bump to `10`. The bound-enforcement semantics are unchanged; only the numeric fidelity matters. [`BatchPackagingClassifierServiceTest.java:306-309`]
- [x] [R2][Patch] R2-P11: Controller Bean-Validation tests asserted `propertyPath.contains("vtsz")` / `.contains("description")` without exercising the indexed path (`pairs[3].vtsz`) required by AC #2 and AC #26(b). Fix: add a 4-pair batch with the invalid entry at index 3 and assert `pairs[3].vtsz`; mirror for description at index 1. [`BatchPackagingClassifierControllerTest.java:121-153`]
- [x] [R2][Defer] D13: No overall deadline on `scope.join()` — a batch of 100 pairs can block a Tomcat worker indefinitely if Vertex degrades past circuit-breaker timeouts. Requires architectural decision (adopt `scope.joinUntil(...)` or rely on upstream deadline). — deferred, follow-up.
- [x] [R2][Defer] D14: Golden fixture `batch-packaging-v1.json` has 10 `(vtsz, description)` pairs spanning the required packaging families, but only 3 descriptions verbatim match `DemoInvoiceFixtures.java` line items; the other 7 are synthesised Hungarian packaging descriptions. Categories covered; text fidelity deferred. — deferred.
- [x] [R2][Defer] D15: `request_cappedExceeded_retrySeconds_present_and_bounded` asserts `retrySeconds > 0 && retrySeconds <= 32*24*3600` but does not compute the exact delta to the next Europe/Budapest month boundary. Acceptably imprecise for a test assertion; exact match is brittle on boundary days. — deferred.
- [x] [R2][Defer] D16: Counter increment can be missed if a forked task is interrupted between `results.set(idx, outcome)` and `incrementStrategyCounter(...)` — observability drift only; affects `classifier.batch.pairs` tallies by at most one tick per interrupted pair. Attribution improvement lives alongside D11 follow-up. — deferred.

### Code Review R2 Follow-up Resolutions (2026-04-20)

All 11 R2 patches landed. Targeted `hu.riskguard.epr.registry.*` tests green, ArchUnit green, full backend suite **BUILD SUCCESSFUL** (9m 42s), frontend 797/797 green, tsc 0 errors, lint 0 errors, lint:i18n 22/22 files OK, Playwright E2E 5 passed / 1 skipped. Story flips to `done`.

## Dev Notes

### What this story is — and what it deliberately is NOT

**IS:**
- A new REST endpoint + DTOs + domain-level batch orchestrator that composes the existing Story 9.3 `ClassifierRouter`.
- A bounded-concurrency Java 25 `StructuredTaskScope` orchestration reusing the codebase's existing concurrency primitive (`CompanyDataAggregator.java:59-88`).
- A per-pair failure-isolation boundary that matches `VertexAiGeminiClassifier.classify()`'s existing exception-to-empty-result contract.
- A per-tenant concurrent-batch gate + monthly-cap pre-check enforced at the controller boundary.
- A Micrometer observability layer (`classifier.batch.pairs`, `classifier.batch.duration`) registered once at startup.

**IS NOT:**
- A rewrite of `ClassifierRouter`, `VertexAiGeminiClassifier`, `VtszPrefixFallbackClassifier`, `ClassifierUsageService`, or `ClassifierUsageRepository`. Story 10.3 consumes them as-is.
- A schema / Flyway migration. No new tables. No new columns. Story 10.1 landed all Epic 10 schema.
- An audit-writer. No `registry_entry_audit_log` rows emitted. That is Story 10.4's job, keyed by the `productId` created at persist time (AC #21).
- A modification of `/api/v1/registry/classify` (the Story 9.3 single-pair endpoint). That endpoint stays on the Registry editor's Suggest button unchanged (AC #29).
- A frontend story. Story 10.3 ships backend-only. Story 10.4 builds the frontend that consumes this endpoint (`InvoiceBootstrapDialog.vue`).
- A user-driven rate limiter. The per-tenant gate (AC #15) protects the backend from runaway concurrency; it does not negotiate with the user beyond a 429 + Retry-After hint.

### Two audit layers — why the classifier does not write `registry_entry_audit_log`

Per **ADR-0003 line 81**: "10.3 | AI classification — `AuditService.recordRegistryFieldChange(...)` with `source=AI_SUGGESTED_CONFIRMED`."

This line describes the *audit row written when the classifier's result is persisted to a Registry row* — which happens in Story 10.4, not Story 10.3. The batch classifier endpoint itself is **stateless**: it receives `(vtsz, description)` pairs, returns packaging chains, and never writes to the DB. `FieldChangeEvent.productId` is declared `@NonNull` at `FieldChangeEvent.java:42`; there is no productId at classification time, so no `FieldChangeEvent` can be constructed.

The correct design — ratified in AC #21 — splits audit into two layers:

1. **Classifier observability audit (Story 10.3, this story).** Micrometer counters per pair, tagged by strategy. Non-compliance-grade; lives in the metrics pipeline for operational dashboards.
2. **Registry-row compliance audit (Story 10.4).** One `registry_entry_audit_log` row per component created, via `AuditService.recordRegistryFieldChange(FieldChangeEvent(productId=<newly-inserted>, tenantId, "components[<id>].kf_code", null, "<kfCode>", userId, AuditSource.AI_SUGGESTED_CONFIRMED, "VERTEX_GEMINI", modelVersion))`.

If a reviewer asks "where's the per-pair compliance audit in Story 10.3?" — the answer is: it does not and cannot exist in a stateless classifier. The compliance trail lands in 10.4 at persist time. The classifier's Micrometer counter (`classifier.batch.pairs`) is the operational complement; it does NOT replace a compliance audit row, and it is NOT a `registry_entry_audit_log` substitute.

### Why `StructuredTaskScope` (Java 25) rather than `CompletableFuture` or Spring `@Async`

The codebase's existing pattern for parallel external calls is `StructuredTaskScope` — see `CompanyDataAggregator.java:16, 59-88`. Adopting a second pattern (CompletableFuture, ExecutorService, or Spring `@Async`) for the batch classifier would fracture the mental model. `StructuredTaskScope.Joiner.awaitAll()` gives us:

- **Virtual threads** — one JVM-cheap thread per pair; 100 pairs ≈ 100 virtual threads, effectively free.
- **Scope-bounded lifetime** — if the caller's thread is interrupted, all forked tasks cancel cleanly.
- **Structured error propagation** — `scope.throwIfFailed()` surfaces a partial failure at scope-exit; though in Story 10.3 we wrap each task body in a `try/catch` that degrades to `UNRESOLVED`, so `throwIfFailed` becomes a defensive no-op.

The `Semaphore(concurrency)` gate inside each task enforces the configured `batch.concurrency` limit — the scope itself forks all 100 tasks immediately; each task acquires a permit before calling Gemini. This is the same throttle pattern as `CompanyDataAggregator`, which runs adapter calls under scope but limits in-flight via the circuit breaker's `slidingWindowSize`.

### Tenant context propagation — why `TenantContext` is not inherited by virtual threads

`TenantContext.getCurrentTenant()` reads from a `ScopedValue` (modern path) or `ThreadLocal` (legacy path). Virtual threads forked inside `StructuredTaskScope` do NOT automatically inherit `ScopedValue` bindings from the parent thread unless the fork runs inside a `ScopedValue.where(...).run(...)` envelope.

`CompanyDataAggregator.java:53, 67` captures `tenantId` as a local variable before forking, then re-establishes context inside each task. Story 10.3's batch classifier follows this exact pattern:

```java
UUID tenantId = TenantContext.getCurrentTenant();  // or from controller, passed in
try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
    for (int i = 0; i < pairs.size(); i++) {
        final int idx = i;
        final var pair = pairs.get(i);
        scope.fork(() -> {
            semaphore.acquire();
            try {
                return withTenant(tenantId, () -> batchClassifyOnePair(pair));
            } finally {
                semaphore.release();
            }
        });
    }
    scope.join();
}
```

Failing to re-establish the tenant context causes `ClassifierRouter:56` to read `TenantContext.getCurrentTenant() == null` and silently skip the monthly cap check (line 59 `if (tenantId != null && …)`) — the per-pair usage increment on line 69 also becomes a no-op. This is a **silent compliance bug**: the classifier returns results but never bills the tenant's cap. Covered by AC #12 and by a test in Task 9 that asserts post-batch `call_count` matches the Gemini-classified pair count.

### Cap pre-check semantics — why "best-effort" not "atomic"

`ClassifierUsageRepository.isCapExceeded(...)` (line 30-37) is a non-locking read. Two concurrent batches from the same tenant could each read `call_count=999` and both pass the pre-check, then collectively consume 200 pairs when only 1 remains. The alternatives are:

- **Pessimistic `SELECT … FOR UPDATE`** per request — serialises all batches per tenant; defeats the concurrency story.
- **Advisory-lock + sub-batch reservation** — complex, introduces lock-timeout ambiguity.
- **Atomic "reserve N" via `UPDATE … WHERE call_count + N <= cap`** — possible but adds a custom column semantics (reservations + calls).

The monthly cap is a **soft budget**, not a hard ceiling. Over-shooting by one batch (worst case ~200 calls) is acceptable; under-shooting by rejecting a legitimate batch at a race condition is not. Story 10.3 documents this trade-off (AC #9) and relies on the concurrent-batch gate (AC #15, max 3 per tenant) + circuit breaker to bound the damage. If production evidence later shows sustained over-shoot, revisit in a follow-up story — not in 10.3's scope.

### Why per-pair increment of `call_count`, not per-batch

The PLATFORM_ADMIN usage dashboard (`/api/v1/admin/classifier/usage`, `EprAdminController.java:80-86`) reports per-tenant monthly cost at 0.15 Ft × `call_count` (CP-5 §4.4). Treating a 100-pair batch as 1 call would understate the actual Vertex AI cost by 99×. Per-pair increments keep the cost metric accurate without touching any downstream dashboard code.

This is already how `ClassifierRouter:69` handles increments (one call = one increment). Story 10.3's batch service fires the same `ClassifierUsageService.incrementUsage(tenantId, inputTokens, outputTokens)` call once per successful Gemini pair — no change to the counter semantics, just more calls.

### Circuit-breaker interaction — what happens when `vertex-gemini` opens mid-batch

Every pair in a batch routes through the **same** `vertex-gemini` CircuitBreaker instance (`VertexAiGeminiClassifier.java:76`, bound to `CircuitBreakerRegistry.circuitBreaker("vertex-gemini")`). Circuit-breaker state is per-instance, not per-call. If the breaker opens during pair #47 of a 100-pair batch:

- Pair #47 catches `CallNotPermittedException` at `VertexAiGeminiClassifier.java:93` → returns `ClassificationResult.empty()`.
- Pairs #48–#100 ALSO hit the open breaker → all return empty → router falls to VTSZ-prefix for each → pair tagged `VTSZ_PREFIX_FALLBACK`.
- No cap increment for pairs #47+ (Gemini never succeeded).
- Batch completes normally from the user's perspective; a large fraction is VTSZ_PREFIX_FALLBACK-tagged — visible in the `classifier.batch.pairs{strategy=VTSZ_PREFIX_FALLBACK}` counter.

This is exactly the graceful-degradation behaviour Story 9.3 designed; Story 10.3 preserves it. AC #14 ratifies this as the expected behaviour; no additional branching needed.

### Previous Story Intelligence

**From Story 10.2 (in review, 2026-04-19):**
- **AC-to-task walkthrough (retro T1) is binding.** 10.2 enforced it and paid 0 AC-gap patches. 9.4 skipped it and paid 25+. Replicate 10.2's discipline here as Task 1.
- **Targeted tests first, full suite once at end.** `./gradlew test --tests "hu.riskguard.epr.*"` ~90 s; ArchUnit ~30 s; frontend ~6 s. No full-suite thrashing during development.
- **Never pipe `gradlew`.** Output buffering breaks on pipes (user memory `feedback_test_timeout_values`).
- **PrimeVue component patterns and i18n alphabetical hook do not apply** — Story 10.3 is backend-only. `lint:i18n` runs as a regression-only check.

**From Story 10.1 (closed 2026-04-18):**
- **`AuditService` is the only path to audit tables.** Story 10.3 does NOT write audit (AC #21), but this architectural rule is binding for Story 10.4 (the classifier's downstream consumer). ArchUnit enforces it — `EpicTenInvariantsTest.only_audit_package_writes_to_audit_tables`.
- **`AuditService` is NOT `@Transactional`.** Likewise binding for 10.4; not relevant in 10.3 since the classifier never invokes AuditService.
- **Tx-pool refactor in 10.1:** NO `@Transactional` method may hold a NAV HTTP call across its span. Story 10.3's batch classifier calls Vertex AI HTTP — the batch service + controller MUST NOT be annotated `@Transactional`. `ClassifierUsageService.incrementUsage(...)` has its own tight `@Transactional` on a single UPSERT — that's correct and does not span the HTTP call.

**From Story 9.3 (closed 2026-04-17):**
- **`ClassifierRouter` is the sole composition boundary.** Any new classifier-consuming path (Story 10.3's batch service) routes through this `@Primary` bean. Do not inject `VertexAiGeminiClassifier` directly — that breaks the fallback logic.
- **`BigDecimal` constructed from JSON via `new BigDecimal(jsonNode.asText())`.** NEVER `BigDecimal.valueOf(asDouble())`. Lesson from 9.3 R2-P2. Story 10.3 only re-maps existing `KfSuggestion.weightEstimateKg` (already a `BigDecimal`) — no new JSON parsing in 10.3 — but if a future sub-task does parse JSON numerics, follow this rule.
- **Defensive bounds at classifier boundary.** `VertexAiGeminiClassifier:241-248` already rejects non-positive or >10000 weights and unitsPerProduct. Story 10.3 applies the same bounds as the *final* layer-selection pass (AC #17) because new prompts or future models may drift.

**From Epic 9 retrospective (2026-04-17):**
- **T1 AC-to-task walkthrough mandatory.** Enforced at Task 1 GATE.
- **T3 numeric precision / BigDecimal.** Applied at AC #17, #18.
- **T5 Spring AI / GCP SDK compatibility matrix in `tech-radar.md` before Story 10.3.** Task 2 creates the doc (minimal viable).
- **T6 i18n alphabetical pre-commit hook.** Runs as regression-only check (no new keys in 10.3).

### What NOT to change in this story

- **DO NOT modify `ClassifierRouter`, `VertexAiGeminiClassifier`, `VtszPrefixFallbackClassifier`.** They are the Story 9.3 composition boundary and stay as-is.
- **DO NOT modify `ClassifyRequest`, `ClassifyResponse`, `KfSuggestionDto`, `KfSuggestion`, `ClassificationResult`, `ClassificationStrategy`.** The single-pair API contract is frozen.
- **DO NOT modify `RegistryClassifyController`.** Its `/api/v1/registry/classify` endpoint serves the Registry editor's Suggest button untouched.
- **DO NOT modify `ClassifierUsageService.isCapExceeded` / `.incrementUsage` / `.getAllTenantsUsage` signatures.** Task 7 *extends* the service with a new `getCurrentMonthCallCount(UUID)` helper — purely additive.
- **DO NOT modify `EprAdminController`** beyond reading usage — its dashboard endpoint stays as-is and reflects batch calls correctly via the per-pair increment chain (AC #22, verified by manual test).
- **DO NOT write `registry_entry_audit_log` rows.** AC #21, ADR-0003 §81. The compliance audit lands in Story 10.4 at persist time.
- **DO NOT add a new Flyway migration.** No schema work in this story.
- **DO NOT modify `application.yml`'s `spring.ai.vertex.ai.gemini.*` config.** Reuse `gemini-3.0-flash-preview` + `europe-west1` + `risk-guard-dev`.
- **DO NOT bump dependencies** (Spring AI, google-auth-library, resilience4j). Stay on versions pinned in `backend/build.gradle`. If a compatibility concern arises during integration testing, flag in Completion Notes; do not upgrade on the branch.
- **DO NOT delete the single-pair prompt** (`prompts/kf-classifier-system-prompt.txt`). Story 10.3 adds `packaging-stack-v1.txt` alongside it.

### Critical Files to Touch

**Backend — new:**
- `backend/src/main/java/hu/riskguard/epr/registry/api/BatchPackagingClassifierController.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/BatchPackagingRequest.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/BatchPackagingResponse.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/BatchPackagingResult.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/PackagingLayerDto.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/ClassifierUsageInfo.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/exception/ClassifierCapExceededException.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/exception/BatchConcurrencyLimitExceededException.java`
- `backend/src/main/java/hu/riskguard/epr/registry/domain/BatchPackagingClassifierService.java`
- `backend/src/main/java/hu/riskguard/epr/registry/domain/BatchPackagingConcurrencyGate.java`
- `backend/src/main/resources/prompts/packaging-stack-v1.txt`
- `backend/src/test/java/hu/riskguard/epr/registry/BatchPackagingClassifierServiceTest.java`
- `backend/src/test/java/hu/riskguard/epr/registry/BatchPackagingClassifierControllerTest.java`
- `backend/src/test/java/hu/riskguard/epr/registry/classifier/BatchPackagingClassifierIntegrationTest.java`
- `backend/src/test/resources/golden/batch-packaging-v1.json`

**Backend — modified:**
- `backend/src/main/resources/application.yml` — add `risk-guard.classifier.batch.concurrency: 10`.
- `backend/src/main/java/hu/riskguard/epr/registry/domain/ClassifierUsageService.java` — add `getCurrentMonthCallCount(UUID)`.
- `backend/src/main/java/hu/riskguard/epr/registry/internal/ClassifierUsageRepository.java` — extract `getCallCountForMonth(UUID, String)` helper; `isCapExceeded` delegates to it.

**Docs — new:**
- `docs/architecture/tech-radar.md` — per retro T5 + AC #33 (minimal-viable consolidation).

**Frontend:** no changes in this story.

**Sprint tracking:**
- `_bmad-output/implementation-artifacts/10-3-ai-batch-classifier-full-packaging-stack-endpoint.md` (this file).
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — add entry, mark `ready-for-dev`.

### Architecture Compliance

- **ADR-0003 (Epic 10 audit architecture):** Story 10.3 does NOT write audit rows (AC #21). ArchUnit rule `only_audit_package_writes_to_audit_tables` stays green because the batch classifier never imports `hu.riskguard.epr.audit.internal.*`.
- **Spring Modulith named interfaces:** no new cross-module boundary. The batch endpoint is in `hu.riskguard.epr.registry.api`; its dependencies (`ClassifierRouter`, `ClassifierUsageService`) are all within the `epr.registry` module.
- **Strict Module Isolation:** no writes to `product_packaging_components` from the batch classifier; `EpicNineInvariantsTest.only_registry_package_writes_to_product_packaging_components` stays green trivially.
- **Tenant Context (`ScopedValue` / `TenantContext`):** re-established inside each forked virtual-thread task (AC #12). Controller reads `active_tenant_id` from JWT; never from request body (AC #6).
- **Java records in `api.dto`; every Response record has `static from(...)` factory:** followed for all 5 new DTOs (AC #3).
- **jOOQ-only persistence:** `ClassifierUsageRepository` already jOOQ; the new helper `getCallCountForMonth` is jOOQ; no JPA.
- **`@Transactional` hygiene (Story 10.1 T4):** `BatchPackagingClassifierController` and `BatchPackagingClassifierService` are NOT `@Transactional` — they span Vertex AI HTTP calls. `ClassifierUsageService.incrementUsage` keeps its existing tight `@Transactional` on a single UPSERT (unchanged by 10.3).
- **Role gating pattern:** `JwtUtil.requireRole(jwt, ..., "SME_ADMIN", "ACCOUNTANT", "PLATFORM_ADMIN")` (JwtUtil.java:49). Matches Story 8.5's `requireAnyAdminRole` pattern.

### Library / Framework Requirements

- **Java 25 (Spring Boot 4.0.3)** — `StructuredTaskScope.open(Joiner.awaitAll())`, virtual threads. No upgrade.
- **Spring AI Vertex Gemini** — `gemini-3.0-flash-preview`, `europe-west1`. Pinned in `application.yml:28-36`. No upgrade.
- **Resilience4j** — existing `vertex-gemini` circuit-breaker config at `application.yml:102-107`. No change.
- **Micrometer** — `MeterRegistry` injected by Spring Boot Actuator; `Counter.builder(...)`, `Timer.Sample` already used in `AuditService`. No upgrade.
- **jOOQ OSS, PostgreSQL 17, Flyway, Testcontainers** — no change.
- **Jakarta Bean Validation** — `@NotNull`, `@NotBlank`, `@Size`, `@Pattern`, `@Valid` — all already in project. No new deps.
- **Jackson ObjectMapper** — reused for any JSON parsing in tests (golden fixture load).
- **JUnit 5 + Mockito + AssertJ** — existing test stack. No upgrade.

### Testing Requirements

- **Real-DB Mandate** (project rule). `BatchPackagingClassifierControllerTest` with `@WebMvcTest` is fine (mocks the service layer). Integration-level DB interaction (the `call_count` increment) happens via `ClassifierUsageService.incrementUsage` which is already covered by `ClassifierUsageServiceTest` (Testcontainers PostgreSQL 17). No new DB test needed unless `getCurrentMonthCallCount` requires one — add a simple repository test in `ClassifierUsageRepositoryTest` (if it exists; else create a minimal one).
- **Targeted tests first** (user memory): `./gradlew test --tests "hu.riskguard.epr.*"` ~90 s. ArchUnit ~30 s. Frontend ~6 s. Full suite ONCE at end.
- **Never pipe `gradlew`** (user memory).
- **Modulith verification.** `ModulithVerificationTest` must pass — Story 10.3 adds no new module boundaries.
- **Contract-First UI.** `npx tsc --noEmit` must pass before ready-for-review — but no frontend changes in 10.3, so this is a regression check.
- **Integration tests gated by env var** — `RG_INTEGRATION_VERTEX_AI=true`. CI `integration-vertex` job is the only caller.

### Test Fixtures

- **`DemoInvoiceFixtures`** (`backend/src/main/java/hu/riskguard/datasource/internal/adapters/demo/DemoInvoiceFixtures.java`) — source of the 10 golden pairs in `batch-packaging-v1.json`. Pick 10 unique `(vtsz, description)` pairs across the fixture's line items; prefer pairs that span different packaging materials (PET, glass, cardboard, metal, paper, plastic film, pallet, wooden).
- **Mock `ClassificationResult`** — test helper factory `ClassificationResultBuilder` (create if not already present under `src/test/java/hu/riskguard/epr/registry/classifier/`) that takes layer specs and returns a populated `ClassificationResult`. Reuse in service + controller tests.

### Project Structure Notes

- **Backend packages (preserve):**
  - `hu.riskguard.epr.registry.api` — REST controllers (`RegistryClassifyController`, new `BatchPackagingClassifierController`). Controllers are the Spring boundary; DTOs live one level down in `api.dto`.
  - `hu.riskguard.epr.registry.api.dto` — request/response records (`ClassifyRequest`, `ClassifyResponse`, `KfSuggestionDto`, + new 5 records).
  - `hu.riskguard.epr.registry.api.exception` — NEW sub-package. Houses `ClassifierCapExceededException`, `BatchConcurrencyLimitExceededException`. If the project convention forbids per-module exception sub-packages, fall back to `hu.riskguard.epr.registry.api` directly — verify by `grep -rn "package hu.riskguard.epr.*.api.exception"` first.
  - `hu.riskguard.epr.registry.domain` — domain services (`ClassifierUsageService`, new `BatchPackagingClassifierService`, new `BatchPackagingConcurrencyGate`).
  - `hu.riskguard.epr.registry.internal` — package-private repositories (`ClassifierUsageRepository`). No new class here.
  - `hu.riskguard.epr.registry.classifier` — classifier interfaces + DTOs (`KfCodeClassifierService`, `ClassifierRouter`, `KfSuggestion`, `ClassificationResult`). **Do NOT add anything new here** — all Story 10.3 code is in `domain` or `api`.
- **Resources:**
  - `backend/src/main/resources/prompts/` — add `packaging-stack-v1.txt` alongside existing `kf-classifier-system-prompt.txt` and `kf-taxonomy-excerpt.txt`.
  - `backend/src/test/resources/golden/` — new folder for `batch-packaging-v1.json`.
- **Conventional commits.** Suggested split:
  - (a) `docs: tech-radar consolidation (Epic 10 T5)` — Task 2.
  - (b) `feat(epic-10): Story 10.3 — batch packaging classifier DTOs + service + concurrency gate` — Tasks 3, 4, 5, 6.
  - (c) `feat(epic-10): Story 10.3 — batch packaging classifier controller + exception handlers` — Task 7.
  - (d) `feat(epic-10): Story 10.3 — prompt template + tests` — Tasks 8, 9.
  - Or one commit per task — either pattern is fine.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` §Story 10.3 (lines 949–978)] — Epic 10 skeleton for this story.
- [Source: `_bmad-output/implementation-artifacts/10-1-registry-schema-menu-restructure-and-tx-pool-refactor.md`] — Story 10.1; source of pattern precedent: tx-pool refactor, `AuditService` facade, module isolation.
- [Source: `_bmad-output/implementation-artifacts/10-2-kf-wizard-browse-button-on-registry.md`] — Story 10.2 (in review 2026-04-19); source of pattern precedent for AC-to-task walkthrough discipline, `lint:i18n` regression check, Task ordering with GATE.
- [Source: `_bmad-output/implementation-artifacts/9-3-ai-assisted-kf-code-classification.md`] — Story 9.3; single-pair classifier foundation.
- [Source: `docs/architecture/adrs/ADR-0003-epic-10-audit-architecture.md` lines 75-88] — "Applied across Stories 10.1–10.9" table; Story 10.3 row mandates `AuditService.recordRegistryFieldChange(...)` with `source=AI_SUGGESTED_CONFIRMED` — in **Story 10.4's persist path**, not 10.3's classifier.
- [Source: `docs/architecture/adrs/ADR-0003-epic-10-audit-architecture.md` line 61] — ArchUnit rule `only_audit_package_writes_to_audit_tables`; binding.
- [Source: `docs/architecture/adrs/ADR-0001-ai-kf-classification.md`] — original AI classifier ADR; orientation for Story 9.3 foundation.
- [Source: `backend/src/main/java/hu/riskguard/epr/registry/classifier/ClassifierRouter.java` (lines 32-92)] — routing logic: Gemini → VTSZ-prefix fallback → empty. The sole composition boundary for Story 10.3's batch service.
- [Source: `backend/src/main/java/hu/riskguard/epr/registry/classifier/internal/VertexAiGeminiClassifier.java` (lines 89-96, 188-291)] — Gemini HTTP call + response parsing + defense-in-depth bounds (lines 241-248 match Story 10.3 AC #17).
- [Source: `backend/src/main/java/hu/riskguard/epr/registry/classifier/internal/VtszPrefixFallbackClassifier.java` (lines 42-85, 71)] — rule-based fallback, single-layer output, `score=0.65`, `modelVersion=null`.
- [Source: `backend/src/main/java/hu/riskguard/epr/registry/domain/ClassifierUsageService.java` (lines 20-64)] — monthly cap + token accumulator. Story 10.3 reads `getCurrentMonthCallCount` (new helper) + reuses `incrementUsage`.
- [Source: `backend/src/main/java/hu/riskguard/epr/registry/internal/ClassifierUsageRepository.java` (lines 30-55)] — atomic upsert pattern (single SQL, no read-then-write race). Story 10.3 adds `getCallCountForMonth(UUID, String)` helper.
- [Source: `backend/src/main/java/hu/riskguard/epr/registry/api/RegistryClassifyController.java` (lines 26-45)] — controller template. Story 10.3's new controller mirrors this shape.
- [Source: `backend/src/main/java/hu/riskguard/epr/api/EprAdminController.java` (lines 77-86)] — existing `/admin/classifier/usage` dashboard; PLATFORM_ADMIN-gated; reflects batch calls automatically (AC #22).
- [Source: `backend/src/main/java/hu/riskguard/epr/registry/api/dto/ClassifyRequest.java`] — single-pair request DTO template.
- [Source: `backend/src/main/java/hu/riskguard/epr/registry/api/dto/ClassifyResponse.java`] — single-pair response DTO + `static from(...)` factory pattern.
- [Source: `backend/src/main/java/hu/riskguard/epr/registry/api/dto/KfSuggestionDto.java` (lines 10-28)] — suggestion DTO shape; multi-layer fields (Story 9.6).
- [Source: `backend/src/main/java/hu/riskguard/epr/registry/classifier/KfSuggestion.java`] — domain record with `layer`, `weightEstimateKg`, `unitsPerProduct`.
- [Source: `backend/src/main/java/hu/riskguard/epr/audit/AuditService.java` (lines 22-35, 64-78)] — audit facade; NOT used by Story 10.3 but cited for 10.4 context.
- [Source: `backend/src/main/java/hu/riskguard/epr/audit/events/FieldChangeEvent.java` (lines 30-49)] — FieldChangeEvent record, `productId @NonNull`; Story 10.3 cannot construct one.
- [Source: `backend/src/main/java/hu/riskguard/epr/audit/AuditSource.java` (lines 15-23)] — enum values; `AI_SUGGESTED_CONFIRMED` is what 10.4's persist path will use.
- [Source: `backend/src/test/java/hu/riskguard/architecture/EpicTenInvariantsTest.java` (lines 44-75)] — ArchUnit Epic-10 invariants; no new rule in 10.3.
- [Source: `backend/src/main/java/hu/riskguard/datasource/internal/CompanyDataAggregator.java` (lines 16, 53, 59-88)] — `StructuredTaskScope` concurrency pattern + tenant-context propagation; Story 10.3 replicates.
- [Source: `backend/src/main/java/hu/riskguard/core/security/Tier.java` / `TierRequired.java`] — `@TierRequired(Tier.PRO_EPR)` annotation.
- [Source: `backend/src/main/java/hu/riskguard/core/util/JwtUtil.java` (lines 28-55)] — `requireUuidClaim`, `requireRole` helpers.
- [Source: `backend/src/main/java/hu/riskguard/core/security/TenantContext.java`] — `ScopedValue`-based tenant resolution.
- [Source: `backend/src/main/resources/application.yml` (lines 28-36, 102-107, 161-163)] — Spring AI Vertex config, `vertex-gemini` circuit breaker, existing `classifier` block (monthly cap + confidence threshold).
- [Source: `backend/src/main/resources/prompts/kf-classifier-system-prompt.txt`] — single-pair prompt template; Story 10.3 adds `packaging-stack-v1.txt` alongside.
- [Source: `backend/src/main/java/hu/riskguard/datasource/internal/adapters/demo/DemoInvoiceFixtures.java`] — source of golden-fixture 10 pairs.
- [Source: `backend/src/test/java/hu/riskguard/epr/registry/classifier/VertexAiGeminiClassifierIntegrationTest.java` (lines 25-67)] — env-gated integration test pattern; replicate for batch.
- [Source: `backend/src/test/java/hu/riskguard/epr/registry/RegistryClassifyControllerTest.java`] — controller test pattern; replicate for batch controller.
- [Source: `_bmad-output/implementation-artifacts/epic-9-retro-2026-04-17.md`] — retro T1 (AC-to-task walkthrough), T3 (BigDecimal discipline), T5 (tech-radar), T6 (i18n alphabetical hook).
- [Source: user memory `project_epic_10_audit_architecture_decision`] — binding cross-cutting audit pattern for Stories 10.2–10.9 (ADR-0003).
- [Source: user memory `feedback_test_timeout_values`] — targeted tests first (~90 s / ~30 s / ~6 s); full suite once at end; never pipe gradlew.

## Dev Agent Record

### Agent Model Used

claude-opus-4-7 (Opus 4.7, 1M context)

### Debug Log References

<!-- To be filled during dev. -->

### Completion Notes List

### Code Review Follow-up Resolutions (2026-04-20)

- ✅ Resolved review finding [Patch]: R1-P1 — `packaging-stack-v1.txt` Pair 3 corrected from VTSZ `73181500` (Csavar) to `76129020` (Alumínium doboz 0,33L) with aluminium-can KF codes (3-layer example).
- ✅ Resolved review finding [Patch]: R1-P2 — Added `per-tenant-concurrent: 3` to `application.yml` under `risk-guard.classifier.batch` for ops discoverability alongside `concurrency: 10`.
- ✅ Resolved review finding [Patch]: R1-P3 — Added `classify_weightAtCeiling_layerAccepted` and `classify_itemsPerParentAtCeiling_layerAccepted` tests verifying exact upper boundary (10000) is accepted.
- ✅ Resolved review finding [Patch]: R1-P4 — Added `classify_allThreeLayersFailDifferentBounds_becomesUnresolved` test: 3 suggestions each failing a different T3 bound (bad weight, bad kfCode, bad itemsPerParent) → UNRESOLVED.
- ✅ Resolved review finding [Patch]: R1-P5 — Added `classify_smeAdminRole_passes` test; all three permitted roles now have dedicated 200-pass tests.
- ✅ Resolved review finding [Patch]: R1-P6 — Added clarifying sentence to `tech-radar.md` AI/Classifier section explaining `spring.ai.*` keys are dead scaffolding config excluded from the runtime classpath.
- ✅ Resolved review finding [Patch]: R1-P7 — Added `log.warn("all {} layers T3-dropped for vtsz={}, strategy={} → UNRESOLVED", ...)` in `BatchPackagingResult.from()` at the all-layers-dropped branch.

### AC-to-Task Walkthrough (T1)

Filed 2026-04-19, before any production code was touched. Every AC mapped to ≥ 1 task; no AC orphaned.

| AC  | Title                                                   | Task(s)        |
|-----|---------------------------------------------------------|----------------|
| 1   | New endpoint POST /api/v1/classifier/batch-packaging    | Task 7         |
| 2   | Request validation (Bean Validation, sizes, patterns)   | Task 4, Task 7 |
| 3   | Response DTO records + from() factories                 | Task 4         |
| 4   | Pair-to-layer mapping (level/units/desc, sort order)    | Task 4, Task 5 |
| 5   | Per-pair failure isolation (no batch-level 500)         | Task 5         |
| 6   | Tier + role gating, tenantId from JWT only              | Task 7         |
| 7   | Cap counted per pair, not per request                   | Task 5         |
| 8   | Batch rejection 429 when remaining cap insufficient     | Task 6, Task 7 |
| 9   | Cap pre-check is best-effort, not atomic (documented)   | Task 7         |
| 10  | ClassifierUsageInfo always returned (200 + 429)         | Task 4, Task 7 |
| 11  | Bounded concurrency; configurable via YAML              | Task 3, Task 5 |
| 12  | Tenant context propagation across forked tasks          | Task 5         |
| 13  | Load-test target ≈ 8s for 20 pairs at concurrency=10    | Task 9         |
| 14  | Circuit breaker shared across pairs                     | Task 5 (composes ClassifierRouter unchanged) |
| 15  | Per-tenant concurrent-batch gate (max 3)                | Task 6, Task 7 |
| 16  | New prompt template packaging-stack-v1.txt              | Task 8         |
| 17  | T3 defensive bounds on AI numerics                      | Task 4, Task 5 |
| 18  | BigDecimal construction discipline (no asDouble)        | Task 4 (mapping uses existing BigDecimal from KfSuggestion; no new JSON parse) |
| 19  | Micrometer counter classifier.batch.pairs{strategy}     | Task 5         |
| 20  | Micrometer timer classifier.batch.duration              | Task 5         |
| 21  | NO registry_entry_audit_log writes (stateless)          | Task 5 (architectural; verified by ArchUnit baseline) |
| 22  | PLATFORM_ADMIN dashboard reflects batch calls           | Task 7 (no code change required; AC satisfied by per-pair increment chain) |
| 23  | VTSZ_PREFIX_FALLBACK tagging when Gemini empty/errors   | Task 4, Task 5 |
| 24  | NONE → "UNRESOLVED" mapping                              | Task 4, Task 5 |
| 25  | BatchPackagingClassifierServiceTest (unit)              | Task 9         |
| 26  | BatchPackagingClassifierControllerTest (@WebMvcTest)    | Task 9         |
| 27  | BatchPackagingClassifierIntegrationTest (env-gated)     | Task 9         |
| 28  | ArchUnit unchanged (no new rule)                        | Task 9 (regression run) |
| 29  | Single-pair endpoint untouched                          | All tasks (DO-NOT-TOUCH list) |
| 30  | ClassifierRouter/Vertex/Vtsz/Usage* unchanged           | All tasks (DO-NOT-TOUCH list) |
| 31  | Existing system prompt unchanged                        | Task 8         |
| 32  | NO Flyway migration                                     | All tasks (no schema work) |
| 33  | tech-radar.md created                                   | Task 2         |
| 34  | T1 walkthrough filed before code                        | Task 1 (this entry) |
| 35  | Full suite green                                        | Task 10        |

Coverage check: ACs 1–35 → tasks 1–10. No gaps. Proceeding to Task 2.

### Implementation decisions

- **Prompt wiring (Task 8).** Recommended path taken — `BatchPackagingClassifierService` loads `prompts/packaging-stack-v1.txt` via `ClassPathResource` in its constructor (fail-fast startup), but `VertexAiGeminiClassifier` is NOT modified. The new prompt is a scaffold for Story 10.5+ to route through when the batch-oriented prompt pays off; today batch pairs go through the existing single-pair `ClassifierRouter`, preserving AC #29/#30/#31 regression guarantees.
- **ArchUnit fix on `BatchPackagingResult`.** Initial draft used a `switch` expression on `ClassificationStrategy` inside the DTO package; javac emits a synthetic `BatchPackagingResult$1` which the `dtos_should_be_records` rule rejects. Replaced with a private static `strategyToString(...)` helper using plain if/else (no synthetic). Rule stays green; code shape is equivalent.
- **Controller test style (AC #26).** Story spec called for `@WebMvcTest` + `@MockBean`; actual pattern in this module (`RegistryClassifyControllerTest`, every other controller test under `hu.riskguard.epr.registry`) is Mockito-only direct construction. Followed the actual pattern — 15 controller tests cover happy path + 4 Bean-Validation cases + 401 + 2×403 + 2×200 (ACCOUNTANT, PLATFORM_ADMIN) + `@TierRequired` reflection assertion + 429 (cap) + 429 (concurrency) + both `@ExceptionHandler` Retry-After assertions. Closer coverage than the spec required.
- **Visibility promotions.** `BatchPackagingResult.STRATEGY_*` constants and `BatchPackagingClassifierService.COUNTER_NAME`/`TIMER_NAME` promoted to `public` because the service (in `.domain`) and tests (in `hu.riskguard.epr.registry`) live in different packages from the DTOs (`.api.dto`).
- **`weightEstimateKg` nullability.** `PackagingLayerDto.weightEstimateKg` is nullable — the T3 bound `(0, 10000]` applies only when present. The VTSZ-prefix fallback emits `weightEstimateKg=null` (`VtszPrefixFallbackClassifier:74`); rejecting nulls at the bound check would have made every fallback layer collapse to UNRESOLVED, contradicting AC #23.
- **Hikari connection-close WARN spam during test shutdown.** Benign. Same pattern as prior stories — `HikariPool-N - Failed to validate connection ...` is logged at app-shutdown hook when the container's Testcontainers Postgres is already being torn down. Build reports SUCCESSFUL.
- **Manual-test note for AC #22.** No code change needed — per-pair increment chain via `ClassifierRouter:69` is already the single billing path. PLATFORM_ADMIN dashboard (`GET /api/v1/admin/classifier/usage`) will reflect batch calls automatically; flagged for manual verification by a reviewer with SME_ADMIN creds.

### File List

**Backend — new (13 files):**
- `backend/src/main/java/hu/riskguard/epr/registry/api/BatchPackagingClassifierController.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/BatchPackagingRequest.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/BatchPackagingResponse.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/BatchPackagingResult.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/PackagingLayerDto.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/dto/ClassifierUsageInfo.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/exception/ClassifierCapExceededException.java`
- `backend/src/main/java/hu/riskguard/epr/registry/api/exception/BatchConcurrencyLimitExceededException.java`
- `backend/src/main/java/hu/riskguard/epr/registry/domain/BatchPackagingClassifierService.java`
- `backend/src/main/java/hu/riskguard/epr/registry/domain/BatchPackagingConcurrencyGate.java`
- `backend/src/main/resources/prompts/packaging-stack-v1.txt`
- `backend/src/test/java/hu/riskguard/epr/registry/BatchPackagingClassifierServiceTest.java`
- `backend/src/test/java/hu/riskguard/epr/registry/BatchPackagingClassifierControllerTest.java`
- `backend/src/test/java/hu/riskguard/epr/registry/classifier/BatchPackagingClassifierIntegrationTest.java`
- `backend/src/test/resources/golden/batch-packaging-v1.json`

**Backend — modified (3 files):**
- `backend/src/main/resources/application.yml` — added `risk-guard.classifier.batch.concurrency: 10`.
- `backend/src/main/java/hu/riskguard/epr/registry/domain/ClassifierUsageService.java` — added `getCurrentMonthCallCount(UUID)` + `getMonthlyCap()`.
- `backend/src/main/java/hu/riskguard/epr/registry/internal/ClassifierUsageRepository.java` — extracted `getCallCountForMonth(UUID, String)`; `isCapExceeded` delegates to it.

**Docs — new (1 file):**
- `docs/architecture/tech-radar.md`

**Sprint tracking — modified (2 files):**
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — Story 10.3 status → `review`.
- `_bmad-output/implementation-artifacts/10-3-ai-batch-classifier-full-packaging-stack-endpoint.md` — task checkboxes, Completion Notes, File List, Status → `review`.

**Frontend:** no changes.

## Change Log

| Date       | Change                                                                                                    |
|------------|-----------------------------------------------------------------------------------------------------------|
| 2026-04-19 | Story file created (SM prep, post-10.2-review). Status: ready-for-dev. AC-to-task walkthrough pending as Task 1 gate. |
| 2026-04-20 | Addressed code review findings — 7 items resolved (R1-P1 through R1-P7): fixed aluminium-can VTSZ in prompt (P1); added `per-tenant-concurrent: 3` to application.yml (P2); added T3 boundary-pass tests for `weightEstimateKg=10000` and `itemsPerParent=10000` (P3); added multi-layer all-dropped test (P4); added `classify_smeAdminRole_passes` test (P5); clarified dead Spring AI config keys in tech-radar.md (P6); added `log.warn` aggregate log when all layers T3-dropped in `BatchPackagingResult.from()` (P7). Targeted tests green (BUILD SUCCESSFUL). ArchUnit green. |
| 2026-04-20 | Story 10.3 implemented end-to-end. 13 new files, 3 modified. Backend-only: `POST /api/v1/classifier/batch-packaging` endpoint + DTOs + `BatchPackagingClassifierService` (Java 25 `StructuredTaskScope` bounded concurrency) + `BatchPackagingConcurrencyGate` (per-tenant max 3 concurrent) + cap pre-check + T3 layer bound filter + Micrometer `classifier.batch.pairs`/`classifier.batch.duration` + `@ExceptionHandler` 429 mappings. 28 new unit tests, 1 env-gated integration test. Full backend: BUILD SUCCESSFUL. ArchUnit green. Frontend 797/797 green, tsc clean, lint 0 errors, lint:i18n 22 files OK. Status: ready-for-dev → review. |
