# Story 1.1: Project Initialization & Monorepo Foundation

Status: done

### Review Follow-ups (AI)

- [x] [AI-Review][CRITICAL] Broken i18n CI Check: script hardcodes non-existent paths (hu.json/en.json) while project uses directory-based i18n [frontend/scripts/i18n-check.js:4]
- [x] [AI-Review][MEDIUM] Architecture Guardrails Bypassed: Critical ArchUnit rules (API paths, DTO factories) are commented out [backend/src/test/java/hu/riskguard/architecture/NamingConventionTest.java]
- [x] [AI-Review][MEDIUM] CI Pipeline Fragility: Steps 1-12 claim is marketing; actual implementation has pathing and integration gaps
- [x] [AI-Review][LOW] Modulith Test effectiveness: Verify if modules are actually being detected from the current package structure

## Dev Agent Record

### Implementation Plan
- Configure GitHub Actions workflow for the 12-step CI pipeline as defined in architecture.md.
- Create ArchUnit and Modulith verification tests to enforce project conventions.
- Update backend build.gradle and frontend package.json with required dependencies and scripts.

### Completion Notes
- Configured `.github/workflows/ci.yml` with the 12-step pipeline, including i18n key parity check and separate unit/integration tests.
- Added ArchUnit `NamingConventionTest` and `ModulithVerificationTest` with rules for table naming, API paths, and DTO structures.
- Created `backend/Dockerfile` for multi-stage containerization.
- Aligned `RiskGuardProperties.java` and `application.yml` with `risk-guard-tokens.json`.
- Updated `docker-compose.yml` with Postgres health checks for CI consistency.
- Added `frontend/scripts/i18n-check.js` for automated translation validation.
- ✅ **Resolved all Review Findings:** Fixed i18n script, re-enabled ArchUnit guardrails, stabilized CI pipeline config, and added ESLint flat config for Nuxt 4.

## File List
- .github/workflows/ci.yml
- backend/Dockerfile
- backend/build.gradle
- backend/src/main/java/hu/riskguard/core/config/RiskGuardProperties.java
- backend/src/main/resources/application.yml
- backend/src/test/java/hu/riskguard/architecture/NamingConventionTest.java
- backend/src/test/java/hu/riskguard/architecture/ModulithVerificationTest.java
- backend/src/main/java/hu/riskguard/identity/api/dto/TenantSwitchResponse.java
- docker-compose.yml
- frontend/package.json
- frontend/nuxt.config.ts
- frontend/scripts/i18n-check.js
- frontend/eslint.config.js
- risk-guard-tokens.json


## Change Log
- 2026-03-06: Initialized CI/CD pipeline and architectural guardrails.
- 2026-03-06: Resolved code review findings and stabilized frontend toolchain.

## Story Completion Status

Status: done
Completion Note: CI/CD pipeline configured and architecture guardrails implemented. All review findings resolved. Project foundation is stable and type-safe.

