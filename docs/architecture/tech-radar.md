---
title: Tech Radar — risk_guard
date: 2026-04-19
author: dev (Story 10.3, Epic 9 retro action T5)
status: Living
---

# Tech Radar

This document is the single, audit-friendly snapshot of the runtime stack and pinned versions for components that the Epic 10 batch classifier (Story 10.3) and downstream Stories 10.4–10.9 depend on. Versions here mirror what is pinned in `backend/build.gradle` and `backend/src/main/resources/application.yml`; if those files diverge from this radar, the build files are authoritative — update this radar.

## AI / Classifier

- **Vertex AI Gemini** — model `gemini-3.0-flash-preview`, location `europe-west1`, project-id env-var `GCP_PROJECT_ID`. Configured at `application.yml:28-36`.
- **Spring AI** — NOT used as a wrapper. Spring AI 1.x is incompatible with Spring Boot 4.0.3 (Spring Framework 7.x), so Gemini is called directly via `java.net.http.HttpClient` from `VertexAiGeminiClassifier` (Story 9.3 decision; see `backend/build.gradle:113-119` comment). Note: `spring.ai.vertex.ai.gemini.*` keys appear in `application.yml` from initial project scaffolding; Spring AI is excluded from the runtime classpath (`build.gradle:113-119`). Those keys are dead config — harmless but misleading.
- **Prompts** — versioned text files under `backend/src/main/resources/prompts/`:
  - `kf-classifier-system-prompt.txt` — single-pair classifier system prompt (Story 9.3).
  - `kf-taxonomy-excerpt.txt` — KF code taxonomy reference (appended to all prompts).
  - `packaging-stack-v1.txt` — batch packaging stack prompt scaffold (Story 10.3; reused by future batch orchestration).
- **Confidence threshold** — `risk-guard.classifier.confidence-threshold: MEDIUM` (`application.yml:162`).
- **Monthly cap** — `risk-guard.classifier.monthly-cap: 1000` calls per tenant per Europe/Budapest calendar month (`application.yml:163`).
- **Batch concurrency** — `risk-guard.classifier.batch.concurrency: 10` (Story 10.3 addition).

## GCP SDKs

- **`com.google.auth:google-auth-library-oauth2-http:1.23.0`** — Application Default Credentials provider for Vertex AI HTTP calls (`backend/build.gradle:119`).
- **`com.google.cloud.sql:postgres-socket-factory:1.21.0`** — Cloud SQL Auth Proxy socket connector for Cloud Run → Cloud SQL (`backend/build.gradle:91`).

## Resilience4j

- **Version** — `io.github.resilience4j:resilience4j-spring-boot3:2.2.0` (`backend/build.gradle:104`).
- **`vertex-gemini` circuit breaker** — `slidingWindowSize: 10`, `failureRateThreshold: 50%`, `waitDurationInOpenState: 60s`, `permittedNumberOfCallsInHalfOpenState: 3`, `registerHealthIndicator: true` (`application.yml:102-107`). Shared by single-pair (Story 9.3) and batch (Story 10.3) classifier paths.
- **Default retry config** — `maxAttempts: 2`, `waitDuration: 1s` (`application.yml:108-112`).
- Other instances: `demo`, `nav-online-szamla`.

## Java runtime

- **Java 25** with `--enable-preview` for `java.util.concurrent.StructuredTaskScope` (JEP 462 / 480). Configured in `backend/build.gradle:25-42`.
- **`StructuredTaskScope.open(Joiner.awaitAll())`** is the codebase's chosen primitive for bounded concurrent external calls. Existing usage: `CompanyDataAggregator.java:59-88`. Story 10.3's `BatchPackagingClassifierService` follows the same pattern. Do NOT introduce CompletableFuture / `@Async` / fixed thread pools for new bulk-call paths.
- **Virtual threads** — forked tasks run on virtual threads (each `scope.fork(...)` invocation), so a 100-pair batch creates 100 virtual threads; concurrency is capped by an explicit `Semaphore(concurrency)` inside each task body, not by the scope itself.

## Spring stack

- **Spring Boot** — `4.0.3` (`backend/build.gradle:14`).
- **Spring Modulith** — `2.0.3` (`backend/build.gradle:55`); used for module boundary checks (`ModulithVerificationTest`) and the JPA-based event publication store.
- **Spring Security OAuth2** — resource-server + client, used for OIDC login and JWT bearer token validation.

## Persistence

- **PostgreSQL 17** runtime; **Testcontainers PostgreSQL 1.20.4** for integration tests.
- **jOOQ OSS 3.19.30** — sole persistence layer. JPA is on the classpath only for Spring Modulith's event publication store; no `@Entity` classes for domain types.
- **Flyway 10.20.1** — schema migrations under `backend/src/main/resources/db/migration/`.

## Observability

- **Micrometer** via `spring-boot-starter-actuator`. New batch-classifier instruments (Story 10.3): counter `classifier.batch.pairs{strategy=GEMINI|VTSZ_PREFIX_FALLBACK|UNRESOLVED}`, timer `classifier.batch.duration{pairCount=1-10|11-50|51-100}`. These complement (do NOT replace) the `audit.writes{source=…}` counter owned by `AuditService` (ADR-0003).

## Notes for future stories

- **Story 10.4** consumes the batch endpoint built in Story 10.3 and is responsible for writing the compliance audit row (`AuditService.recordRegistryFieldChange(...)` with `source=AI_SUGGESTED_CONFIRMED`) at persist time. The batch classifier itself never writes to `registry_entry_audit_log` (no `productId` available; see ADR-0003 §"Applied across Stories 10.1–10.9" line 81 and Story 10.3 AC #21).
- **Story 10.5+** may swap `prompts/packaging-stack-v1.txt` for a multi-pair-per-call variant; the file is loaded but not yet routed through `VertexAiGeminiClassifier` in 10.3. Do not delete the resource — it is referenced by the smoke test in `BatchPackagingClassifierServiceTest`.
