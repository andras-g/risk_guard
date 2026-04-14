# Task 01: Research — OKIRkapu Submission Format + KF Code 2026-01-01 Refresh

**Goal:** Resolve two unknowns that block the design of Story 9.4 (MOHU export) and Story 9.1 (registry data model).
**Scope:** Research only. No code. Produce a written research memo.
**Skill:** `bmad-technical-research`.
**Prerequisite:** None.
**Estimated effort:** 30–60 minutes.

---

## Prompt for the new session

```
/bmad-technical-research

Context: Sprint Change Proposal CP-5 (_bmad-output/planning-artifacts/sprint-change-proposal-2026-04-14.md) introduced Epic 9 (Product-Packaging Registry). §8 of CP-5 lists two open questions that gate the design of Story 9.1 and Story 9.4. I need both resolved before PRD / Architecture work starts.

=== Part 1: OKIRkapu submission format ===

Question: What format does OKIRkapu accept for EPR quarterly data submissions from producers?

Why it matters: Story 9.4 ("Registry-Driven EPR Autofill + MOHU CSV Export") assumes CSV based on the PO's recollection. If it's actually XML (with a published XSD) or API-only, the exporter design changes substantially.

Specifically answer:
- What format does OKIRkapu accept? CSV, XML, API, manual web form?
- Is there a published schema, template, or example file?
- Is the submission per-transaction (line-level) or aggregated (per KF code totals)?
- What fields are mandatory per row/submission?

Search hints:
- "OKIRkapu EPR adatszolgáltatás formátum"
- "OKIR EPR bevallás CSV XML"
- "OKIRkapu gyártói nyilvántartás feltöltés"
- MOHU site (mohu.hu) — data reporting guides
- Kormanyhivatalok.hu — EPR reporting guidance PDFs
- 80/2023 Korm. rendelet 4. melléklet (per-transaction reporting fields)
- 8/2023. (VI. 2.) EM rendelet (fee schedule)

If a definitive answer is not publicly available, say so explicitly and note where the user should verify (e.g., "confirm with MOHU account manager" or "download OKIRkapu guide from account portal").

=== Part 2: KF code 2026-01-01 refresh ===

Question: What exactly changes in the KF code system on 2026-01-01?

Why it matters: Story 9.1 stores KF codes on product packaging components. If IDs change, Story 9.1 needs a migration plan from day one.

Specifically answer:
- What is changing on 2026-01-01? New codes added, existing codes deprecated, IDs changed, semantics changed?
- Is there an official mapping (old → new) published anywhere?
- Does the 8-digit structure itself change, or only specific code values within it?
- Is there a cutover date by which old codes become invalid?

Search hints:
- "KF kód változás 2026"
- "KF kódok módosítás 2026 január"
- "80/2023 KF kód 1. melléklet módosítás"
- Magyar Közlöny 2025 late issues for amendments to 80/2023
- NAK, Deloitte, Andersen tax bulletins on 2026 EPR changes

=== Output ===

Write the findings to: _bmad-output/planning-artifacts/research/okirkapu-and-kf-refresh-2026-04-XX.md (use today's date).

Structure:
# OKIRkapu Format + KF Refresh Research

## Part 1 — OKIRkapu submission format
### Answer
### Evidence (URLs + quotes)
### Confidence (High / Medium / Low)
### Impact on Story 9.4 design

## Part 2 — KF code 2026-01-01 refresh
### Answer
### Evidence
### Confidence
### Impact on Story 9.1 data model
### Migration plan sketch (if codes change)

## Open questions (items that need human verification)

Reference artefacts to load:
- _bmad-output/planning-artifacts/sprint-change-proposal-2026-04-14.md (authoritative CP-5)
- _bmad-output/planning-artifacts/epr-packaging-calculation-gap-2026-04-14.md (original gap analysis)
```

## Success criteria

- Both parts answered with cited sources, or explicit "could not find, needs human verification".
- "Impact on Story X design" sections say concretely what this changes in CP-5's proposed design.
- Confidence level marked for each part.

## Do NOT

- Propose design changes to CP-5. Only document what the research changes as input to Tasks 02 (PRD) and 03 (Architecture).
- Guess. If information isn't public, say so.
