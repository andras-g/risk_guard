---
stepsCompleted: [1, 2, 3, 4]
inputDocuments: ["/home/andras/dev/risk_guard/partnerRadar.md"]
date: 2026-03-04
author: Andras
---

# Product Brief: risk_guard

<!-- Content will be appended sequentially through collaborative workflow steps -->

## Executive Summary

risk_guard (PartnerRadar) is a specialized B2B SaaS platform designed to empower Hungarian SMEs (5-50 employees) with instant, deterministic insights into partner reliability and automated EPR (Extended Producer Responsibility) compliance. By bridging the gap between expensive, complex enterprise data providers and slow, error-prone manual processes, PartnerRadar provides a "digital-first" shield against bad debt and regulatory risks. The platform transforms raw public data into clear, AI-driven risk summaries and automated monitoring, enabling small businesses to operate with the agility and security of a large corporation at a fraction of the cost.

---

## Core Vision

### Problem Statement

Small and medium-sized Hungarian enterprises (SMEs) are disproportionately affected by partner insolvency and the complexity of new EPR regulations. They are forced to choose between expensive, "over-engineered" enterprise solutions like Opten or relying on manual, slow, and error-prone checks. This "information gap" leads to avoidable bad debt, wasted administrative hours, and the constant risk of non-compliance penalties.

### Problem Impact

- **Financial Loss:** A single "failed" partner can result in millions of HUF in bad debt, potentially threatening the SME's survival.
- **Operational Inefficiency:** Manual EPR checks and partner screening are "boring" and time-consuming, pulling owners away from growth activities.
- **Competitive Disadvantage:** SMEs remaining in manual, paper-based workflows cannot compete with more digitized, efficient rivals.

### Why Existing Solutions Fall Short

- **Enterprise Focus:** Current leaders (Opten, Bisnode) are built for large corporations, featuring complex UIs and high monthly retainers (50k-100k+ HUF) that don't fit the SME budget or workflow.
- **Manual Dependency:** EPR compliance is currently handled by expensive consultants (50k-150k HUF/session) via manual, one-off interactions rather than continuous, automated software.
- **Data vs. Insights:** Existing tools provide "raw data" but lack the deterministic, easy-to-read "AI Summaries" that a busy KKV owner needs to make a decision in 30 seconds.

### Proposed Solution

PartnerRadar provides an "Opten lite" experience focused on the 20% of features that provide 80% of the value for SMEs. The solution offers:
1.  **30-Second Screening:** Deterministic "Reliable/Unreliable" reports based on NAV, e-Cégjegyzék, and insolvency data.
2.  **AI-Enhanced Clarity:** Human-readable risk summaries in Hungarian (using 2026-era OpenAI o3/GPT-5 reasoning) to eliminate guesswork.
3.  **Automated Shield:** Continuous monitoring with email alerts for any changes in a partner's status.
4.  **EPR Module:** A digital-first compliance engine with calculators and deadline reminders to replace manual consulting, fed by official legislative XML feeds.

### Key Differentiators

- **Price-to-Value King:** Positioning at 10% of the cost of enterprise competitors (9.9k - 29.9k HUF/month).
- **The "First" in EPR:** The only Hungarian SaaS offering an integrated EPR compliance module.
- **AI-Native Reports:** Moving from "showing data" to "providing answers" through Hungarian language LLM analysis.
- **Digital Empowerment:** Built specifically to help small companies become more digital and competitive through an extensible, modern tech stack (Spring Boot 3.4 / Java 25 / GCP).

---

## Target Users

### Primary Users

**Persona: Gábor, the Busy SME Owner**
- **Context:** Owner of a 10-20 person manufacturing or trade company in Hungary.
- **Motivations:** Avoiding "bad debt" and minimizing administrative "paperwork" (EPR).
- **Current Behavior:** Checks `e-Cégjegyzék` manually, but forgets to monitor partners after the initial check.
- **Problem Experience:** Feels "Opten" is too expensive for a non-finance person; feels overwhelmed by the 2024/2026 EPR changes.
- **Success Vision:** A mobile-friendly dashboard where a "Green Shield" means he can sign, and a "Red Warning" includes a 2-sentence explanation of why.

**Persona: Judit, the Independent Bookkeeper**
- **Context:** External accountant serving 30-50 small KKV clients.
- **Motivations:** Reducing the time spent on manual EPR calculations and protecting her clients from insolvency risks.
- **Problem Experience:** Spends hours parsing MOHU PDF/XML tables and manually checking NAV debt lists for all clients.
- **Success Vision:** A "Pro Accountant" view where she can manage 50 clients' EPR statuses in one interface and trigger bulk partner checks.

### Secondary Users

- **Sales Managers:** Use the 30-second screening during field visits to qualify leads before promising payment terms.
- **Procurement Leads:** Use the monitoring feature to ensure critical suppliers are financially stable before making major down-payments.

### User Journey

1. **Discovery:** Gábor hears about PartnerRadar from his accountant (Judit) or finds it during a Google search for "NAV adóslista lekérdezés."
2. **Onboarding:** "Zero-Friction Search." Gábor types a tax number on the landing page. He gets his first "30-Second Verdict" report for free without registration.
3. **The "Aha!" Moment:** Gábor adds his top 5 partners to a "Watchlist." Two days later, he receives an email: *"Alert: [Partner X] status changed to 'At Risk' (NAV Debt detected)."* He realizes the system is working while he sleeps.
4. **Core Usage:** Gábor opens the app weekly to check his "Risk Dashboard" and uses the EPR Calculator every quarter to prepare his MOHU report.
5. **Success Moment:** Judit (the accountant) saves 4 hours of work using the automated EPR export, and Gábor avoids a 2M HUF loss by asking for prepayment from an "At Risk" partner.

---

## Success Metrics

### User Success
- **Time-to-Verdict:** < 30 seconds for a fresh partner check.
- **Onboarding Simplicity:** 90% of users should run their first check without technical assistance.
- **EPR Relief:** 75% reduction in perceived administrative time for reporting (compared to manual consultants).
- **Deterministic Reliability:** 100% accuracy on core factual data points (NAV Debt status, Insolvency proceedings).

### Business Objectives
- **3-Month Milestone:** 10-20 "Innovator" customers paying for the service; 100% scraper stability.
- **6-Month Milestone:** "Referral Loop" active, with 20% of new signups coming from user recommendations (specifically bookkeepers).
- **12-Month Milestone:** Reaching the 1.5M Ft/hó revenue target with ~100 active SMEs.
- **Sustainability:** Maintenance efforts remain < 10 hours/week for the solo developer.

### Key Performance Indicators (KPIs)
- **Scraper Health Rate:** > 98% success rate in data retrieval.
- **Test Coverage:** > 90% for core business logic and data ingestion pipelines.
- **Conversion Rate:** > 15% from the 14-day free trial to a paid subscription.
- **AI Verdict Accuracy:** > 98% alignment with "Ground Truth" expert validation.

### Visionary Roadmap (Post-MVP)
- **v1.1: AI Self-Healing:** Automated repair of scraper adapters using LLM-driven DOM analysis.
- **v2.0: AI Anomaly Detection:** Identifying "hidden" financial risks in partner data trends.
- **v2.0: AI Admin Assistant:** Automated drafting of MOHU justifications and reports for bookkeepers.
