---
stepsCompleted: [1, 2, 3, 4, 5]
inputDocuments: []
workflowType: 'research'
lastStep: 5
research_type: 'technical'
research_topic: 'Scraper Tech Audit: Java/JSoup vs. Playwright and EPR Data Sources'
research_goals: 'Compare JSoup vs. Playwright for Hungarian Govt Sites, identify anti-bot measures, hidden APIs, and technical data sourcing for EPR compliance (MOHU, fee tables)'
user_name: 'Andras'
date: '2026-03-04'
web_research_enabled: true
source_verification: true
---

# Research Report: technical

**Date:** 2026-03-04
**Author:** Andras
**Research Type:** technical

---

## Technical Research Scope Confirmation

**Research Topic:** Scraper Tech Audit: Java/JSoup vs. Playwright and EPR Data Sources
**Research Goals:** Compare JSoup vs. Playwright for Hungarian Govt Sites, identify anti-bot measures, hidden APIs, and technical data sourcing for EPR compliance (MOHU, fee tables)

**Technical Research Scope:**

- **Architecture Analysis**: Resilient Java-based scraper design and automated EPR data ingestion patterns.
- **Implementation Approaches**: JSoup vs. Playwright comparison for Java/Spring; strategy for managing local EPR lookup tables.
- **Technology Stack**: Scraper libraries, anti-bot bypass, and PDF/Excel parsing for government data.
- **Integration Patterns**: Hidden/Official APIs for NAV/e-Cégjegyzék; MOHU technical integration possibilities.
- **Performance Considerations**: Cloud Run optimization for browser-based (Playwright) vs. lightweight (JSoup) scrapers.

**Research Methodology:**

- Current web data with rigorous source verification
- Multi-source validation for critical technical claims
- Confidence level framework for uncertain information
- Comprehensive technical coverage with architecture-specific insights

**Scope Confirmed:** 2026-03-04

---

## Technology Stack Analysis (2026 Update)

### Programming Languages
**Java 25 (LTS)**
The primary language for the scraper engine. Utilizing **Virtual Threads (Project Loom)** to handle high-concurrency tasks without heavy thread overhead.
_Source: https://openjdk.org/projects/jdk/25/_

### Development Frameworks and Libraries
- **Spring Boot 3.4+**: The framework for modular monolithic architecture.
- **JSoup**: For high-performance, lightweight parsing of static government HTML.
- **Playwright (Java) with Sidecar Chrome**: Mandatory for 2026-era sites using Shadow DOM and anti-bot fingerprints.
- **Tabula-java / Apache POI**: For parsing EPR fee tables from official gazette XML/PDFs.
_Source: https://playwright.dev/java/_

### Database and Storage Technologies
**PostgreSQL 17 (Cloud SQL)**
- **JSONB Snapshots**: Storing full partner state to detect historical "Deltas."
- **BRIN Indexes**: Optimized for time-series snapshot queries on the `created_at` column.
_Source: https://www.postgresql.org/docs/17/index.html_

### Cloud Infrastructure and Deployment
**Google Cloud Platform (GCP)**
- **Cloud Run & Cloud Run Jobs**: For serving the API and executing background scraper tasks respectively.
- **Artifact Registry**: Managing container images.
_Source: https://cloud.google.com/run_

---

## Integration Patterns Analysis

### API Design Patterns
**RESTful "Verdict" API**
Provides deterministic `Reliable/At-Risk` statuses. Designed with B2B extensibility in mind via Webhook support.

### Data Formats and Standards
**Normalized CompanyRecord**
A unified JSON structure that maps data from disparate sources (NAV JSON exports, e-Cégjegyzék HTML, MOHU XML).

### System Interoperability Approaches
**Resilience4j Patterns**
Using Circuit Breakers and Retries to manage the fragility of 3rd party government portals.
_Source: https://resilience4j.readme.io/_

---

## Architectural Patterns and Design

### System Architecture
**Modular Monolith (Spring Modulith)**
Isolating `screening`, `epr`, and `notifications` to ensure high maintainability for a solo developer.

### Design Principles
**Hexagonal Architecture (Ports & Adapters)**
Decoupling the scraper logic from the domain, allowing seamless switching between JSoup, Playwright, or direct API ingestion.

### Data Architecture
**Append-Only Snapshotting**
A temporal data pattern that preserves history and allows the AI to analyze trends (e.g., "This company was green for 2 years but just hit the debt list").

---

## Implementation Approaches and Technology Adoption

### Technology Adoption Strategies
**"Ingestion-First" Approach**
Prioritizing the stable NAV JSON exports (2025 Open Data Portal) before falling back to more fragile scraping methods.

### Deployment and Operations
**Stateless Workers**
Cloud Run Jobs handle the browser-heavy tasks, while the web API remains lean and fast.

### Technical Research Recommendations

**Updated AI Stack (2026)**
- **Model:** **OpenAI o3** or **GPT-5**. These 2026-era models are optimized for complex reasoning and "System 2" thinking, which is critical for generating deterministic risk verdicts from conflicting data points.
- **Framework:** **Spring AI** for native integration.

**Implementation Roadmap**
- **Week 1:** Setup modular monolith; implement NAV JSON ingestion.
- **Week 2:** Build JSoup adapters and EPR decision engine.
- **Week 3:** Integrate **OpenAI o3/GPT-5** for AI Risk Summaries.
- **Week 4:** Deploy to Cloud Run; implement Sentry/Cloud Monitoring.

### Success Metrics
- **Performance:** < 30s for a fresh partner check.
- **Accuracy:** > 98% matching between scraped status and AI verdict.
- **Cost:** < $50/month infrastructure for the first 100 users.
