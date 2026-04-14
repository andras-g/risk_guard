# Architecture Decision Records

This folder collects Architecture Decision Records (ADRs) for Risk Guard. ADRs capture significant architectural choices, the context that made them necessary, and the consequences they accept.

## Format

We use a lightweight [MADR](https://adr.github.io/madr/)-style template. Each ADR file lives at `ADR-<NNNN>-<kebab-case-slug>.md` and contains the following sections:

```markdown
# ADR-NNNN: <Short title>

- **Status:** Proposed | Accepted | Superseded by ADR-XXXX | Deprecated
- **Date:** YYYY-MM-DD
- **Deciders:** <names / roles>
- **Related artefacts:** <PRD/CP/research links>

## Context

What forces, constraints, and prior decisions led to this choice. Plain language; cite legal text, research, or earlier ADRs by path.

## Decision

The choice itself, stated as a present-tense imperative ("We use X for Y"). One paragraph; specifics live under Consequences.

## Consequences

What follows from the decision — both the desirable effects (capabilities unlocked) and the costs (operational debt, future migrations, lock-in). Be honest about both.

## Alternatives considered

For each rejected option: a one-line description, a one-line rationale for rejection, and where it could be revisited.

## Revisit triggers

Concrete signals that should reopen this ADR (e.g., "validation accuracy < 70% on held-out set", "EU registry XSD published"). Without these, ADRs ossify.
```

## Numbering

- IDs are zero-padded four digits, assigned monotonically (`ADR-0001`, `ADR-0002`, ...).
- Never renumber. If an ADR is wrong, write a new one with `Status: Supersedes ADR-XXXX` and update the old one's status to `Superseded by ADR-YYYY`.
- Keep `INDEX.md` in sync — it is the authoritative listing.

## When to write an ADR

Write an ADR when the decision:

- Constrains future implementation choices across modules (e.g., "all AI calls go through one strategy interface").
- Picks one external dependency over alternatives (e.g., a vendor, a protocol, a schema target).
- Introduces a new architectural invariant that ArchUnit or code review must enforce.
- Reverses or amends a previous architectural decision.

Routine implementation choices (a library version bump, a single class refactor) do not need an ADR — code review and the planning artefacts cover those.

## Where ADRs sit relative to `architecture.md`

`_bmad-output/planning-artifacts/architecture.md` is the consolidated architecture document produced by the BMad workflow. ADRs are the per-decision drill-down: `architecture.md` summarises *what* we built and *why* in narrative form; ADRs preserve the decision-time reasoning, alternatives, and revisit triggers for any single choice.

When an ADR is accepted, mention it in the relevant section of `architecture.md` rather than duplicating the prose.
