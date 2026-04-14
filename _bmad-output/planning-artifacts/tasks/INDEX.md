# CP-5 Follow-up Tasks — Epic 9 Preparation

These tasks carry out the 5-step plan for Epic 9. Step 1 (Story 8.3 UI copy patch) was already shipped in the CP-5 session — the 4 tasks below cover the remaining steps.

Each file is a **self-contained prompt** that can be pasted into a fresh Claude Code session.

## Ordering and dependencies

```
task-01 (OKIRkapu + KF research)    ──┐   research, feeds PRD / Architecture
                                       ▼
task-02 (PRD update)                ──┐   formal BMad pipeline begins
task-03 (Architecture ADRs)         ──┘   can run parallel to PRD
                  │
                  ▼
task-04 (Epic 9 + stories)              depends on PRD + Architecture
```

## Task list

| # | File | Scope | BMad skill | Est. effort |
|---|---|---|---|---|
| 01 | `task-01-research-okirkapu-and-kf-refresh.md` | Research | `bmad-technical-research` | 30–60 min |
| 02 | `task-02-prd-update.md` | Planning | `bmad-edit-prd` | 1 h |
| 03 | `task-03-architecture-adrs.md` | Planning | `bmad-create-architecture` | 2 h |
| 04 | `task-04-epic-9-stories.md` | Planning | `bmad-create-epics-and-stories` | 1 h |

## Parent artefacts (referenced by all tasks)

- `_bmad-output/planning-artifacts/sprint-change-proposal-2026-04-14.md` — **the authoritative decision record** for Epic 9
- `_bmad-output/planning-artifacts/epr-packaging-calculation-gap-2026-04-14.md` — original gap analysis
- `_bmad-output/planning-artifacts/prd.md` — current PRD
- `_bmad-output/planning-artifacts/epics.md` — current epic/story list
- `_bmad-output/planning-artifacts/architecture.md` — current architecture

## How to run a task in a new session

1. Open a fresh Claude Code session in this repo.
2. Paste the full contents of the chosen task file as the opening message.
3. The task file carries enough context (references, decisions, constraints) that the new session can act without back-and-forth.
