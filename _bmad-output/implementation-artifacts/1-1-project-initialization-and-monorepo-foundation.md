# Story 1.1: Project Initialization & Monorepo Foundation

Status: done

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

## File List
- .github/workflows/ci.yml
- backend/Dockerfile
- backend/build.gradle
- backend/src/main/java/hu/riskguard/core/config/RiskGuardProperties.java
- backend/src/main/resources/application.yml
- backend/src/test/java/hu/riskguard/architecture/NamingConventionTest.java
- backend/src/test/java/hu/riskguard/architecture/ModulithVerificationTest.java
- docker-compose.yml
- frontend/package.json
- frontend/nuxt.config.ts
- frontend/scripts/i18n-check.js
- risk-guard-tokens.json


## Change Log
- 2026-03-06: Initialized CI/CD pipeline and architectural guardrails.

## Story Completion Status

Status: done
Completion Note: CI/CD pipeline configured and architecture guardrails implemented. Ready for review.

