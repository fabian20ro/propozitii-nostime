# AI Agent Configuration Setup Guide

> **Purpose:** This document serves two roles:
> 1. **Setup guide** — step-by-step instructions for creating all config files from scratch
> 2. **Maintenance protocol** — hand this document to an agent periodically to audit and clean all files, preserving only what's still useful
>
> Apply when: starting a new project, onboarding to an existing one, or running a periodic hygiene pass (weekly/monthly/yearly).

---

## Research Context — Why This Guide Exists

This guide is informed by two key studies and extensive practitioner experience:

- **[Evaluating AGENTS.md](https://arxiv.org/abs/2602.11988)** — Found that LLM-generated context files (via `/init`) **reduced** task success rates by ~3% on average while **increasing inference cost by 20%+**. Developer-provided files only improved performance by ~4% — marginal at best. Context files encouraged broader but less focused exploration.
- **[SkillsBench](https://arxiv.org/abs/2602.12670)** — Found that **curated, focused skills** (2–3 modules) outperform comprehensive documentation. Self-generated skills provide no benefit on average. Smaller models with good skills can match larger models without them.

**Core principle:** Help the model. Don't distract it. If the info is already in the codebase, it probably doesn't need to be in the config file.

---

## How the Files Work Together

There are four file types, each with a distinct role. Understanding how they synchronize is essential — overlap between them is a bug, not a feature.

```
┌─────────────────────────────────────────────────────────────────────┐
│                        AGENT STARTS TASK                            │
│                                                                     │
│  1. Reads AGENTS.md (always in context — bootstrap only)            │
│     → Non-discoverable constraints, legacy traps, file references   │
│     → Tells agent: read LESSONS_LEARNED.md + which sub-agents exist │
│                                                                     │
│  2. Reads LESSONS_LEARNED.md (curated wisdom)                       │
│     → Validated corrections and patterns from past sessions         │
│     → This is where "the agent keeps doing X wrong" lives           │
│                                                                     │
│  3. If task is complex → delegates to sub-agent                     │
│     → .claude/agents/architect.md, planner.md, etc.                 │
│     → Focused procedural knowledge for specific domains             │
│                                                                     │
│  4. Does the work                                                   │
│                                                                     │
│  5. End of iteration:                                               │
│     → ALWAYS appends to ITERATION_LOG.md (raw, what happened)       │
│     → If reusable insight → adds to LESSONS_LEARNED.md              │
│     → If something was surprising → flags to developer              │
│                                                                     │
│  Developer decides:                                                 │
│     → Fix the codebase? (preferred)                                 │
│     → Add to LESSONS_LEARNED.md? (if codebase fix isn't possible)   │
│     → Add to AGENTS.md? (only if it's a non-discoverable constraint │
│       that must be known BEFORE reading any other file)              │
│     → Create a new sub-agent? (invoke agent-creator)                │
│                                                                     │
│  PERIODIC MAINTENANCE (weekly/monthly/yearly/new model):            │
│     → Hand this document to an agent as a standalone task            │
│     → Agent audits ALL files using the Maintenance Protocol          │
│     → Stale entries archived, patterns promoted, overlaps removed    │
│     → Files get leaner over time — never fatter                     │
└─────────────────────────────────────────────────────────────────────┘
```

### What lives where — the boundary rules

| Question | → File |
|----------|--------|
| Can the model discover this from the codebase? | **Nowhere.** Don't write it down. |
| Is this a constraint the model needs BEFORE it starts exploring? | **AGENTS.md** (e.g., "use pnpm not npm", "dev server already running") |
| Is this a correction for a repeated mistake? | **LESSONS_LEARNED.md** (not AGENTS.md) |
| Is this a raw observation from a single session? | **ITERATION_LOG.md** |
| Is this focused procedural knowledge for a recurring task domain? | **Sub-agent** in `.claude/agents/` |
| Does something in the codebase keep confusing agents? | **Fix the codebase first.** Then LESSONS_LEARNED if needed. |

### Promotion flow

```
Observation (single session)
  → ITERATION_LOG.md (always)

Same issue appears 2+ times in ITERATION_LOG
  → Promote to LESSONS_LEARNED.md

Lesson becomes obsolete (dependency removed, API changed, model improved)
  → Move to Archive section in LESSONS_LEARNED.md (never delete)

New recurring task domain emerges
  → Invoke agent-creator → new sub-agent in .claude/agents/

New model release
  → Delete AGENTS.md entirely. Test. Re-add only what's still needed.
  → Review LESSONS_LEARNED.md — many entries may be obsolete.

Periodic maintenance (weekly/monthly/yearly)
  → Hand this document to an agent: "Run the Periodic Maintenance Protocol"
  → Agent audits ALL files, removes stale info, promotes unhandled patterns
  → Agent produces a maintenance report for developer review
  → Files get leaner over time, never fatter
```

---

## File Structure Overview

After applying this guide, your project root should contain:

```
project-root/
├── AGENTS.md                 # Bootstrap context (minimal, non-discoverable constraints only)
├── CLAUDE.md                 # Redirect → AGENTS.md
├── LESSONS_LEARNED.md        # Curated corrections and validated wisdom
├── ITERATION_LOG.md          # Append-only session journal
└── .claude/
    └── agents/               # Specialized sub-agents
        ├── architect.md      # System design, ADRs
        ├── planner.md        # Multi-step implementation plans
        ├── agent-creator.md  # Meta-agent: creates new specialized agents
        └── ux-expert.md      # UI/UX decisions (frontend projects only)
```

---

## Step 1: Create `CLAUDE.md` (Redirect Only)

Create `CLAUDE.md` in the project root with **exactly** this content:

```markdown
Read AGENTS.md
```

Nothing else. This ensures any tool that looks for `CLAUDE.md` is immediately redirected to the canonical file.

---

## Step 2: Create `AGENTS.md` (Bootstrap Only)

This file is always in the agent's context window. Every token here costs attention on every single request. Therefore: **only include things the model needs to know BEFORE it starts exploring the codebase.**

### What DOES NOT belong in AGENTS.md

These are things the model discovers on its own. Including them wastes context, biases toward stale info, and increases cost by 20%+:

- ❌ Project architecture overviews (the model reads your file tree, imports, configs)
- ❌ Dependency lists (the model reads `package.json`, `pom.xml`, `requirements.txt`)
- ❌ Available scripts/commands (the model reads `package.json` scripts, `Makefile`)
- ❌ Folder structure descriptions (the model uses `find`, `ls`, `rg` to explore)
- ❌ Code style rules the linter already enforces
- ❌ General best practices the model already knows (SOLID, DRY, etc.)
- ❌ Anything produced by `/init` — delete it
- ❌ Corrections for repeated mistakes — these belong in `LESSONS_LEARNED.md`

### What DOES belong in AGENTS.md

Only things the model **cannot discover** and **needs before starting work**:

- ✅ Non-obvious tooling constraints (e.g., "Use pnpm, not npm — workspaces break otherwise")
- ✅ Environment assumptions (e.g., "Dev server is always already running — do not start it")
- ✅ Legacy traps that would mislead on first encounter (e.g., "TRPC routes in `/api/legacy/` are deprecated — use Convex")
- ✅ References to LESSONS_LEARNED.md, ITERATION_LOG.md, and sub-agents (so the agent knows the system exists)

### Maintenance Philosophy

AGENTS.md should **shrink over time**, not grow:

- **Monthly audit:** Delete entries for behaviors the model no longer exhibits.
- **New model release:** Delete AGENTS.md entirely. Test. Re-add only what's still needed.
- **Never use `/init`:** Auto-generated content is worse than no content (−3% success, +20% cost).
- **Prefer fixing the codebase** over adding entries. Better tests, clearer naming, proper linting > more config text.

---

## Step 3: Create Sub-Agents in `.claude/agents/`

Sub-agents provide focused procedural knowledge for specific domains. Per SkillsBench: curated, focused skills (2–3 modules) raise pass rates by 16.2pp on average. Self-generated skills provide no benefit.

See existing agents in `.claude/agents/` for structure and conventions.

### Agent Design Rules

1. **Focus (2–3 Modules Maximum)** — An agent covering everything helps with nothing.
2. **Mandatory Structure** — Each agent must have: frontmatter (name, description, tools, model), When to Activate (3+ triggers), Role, Output Format (concrete templates), Principles (3-5 actionable items).
3. **Anti-Patterns** — Don't include discoverable info, don't duplicate AGENTS.md/LESSONS_LEARNED, don't create overlapping agents, keep under 100 lines.
4. **Registration** — After creating an agent, update the Sub-Agents table in `AGENTS.md`.

---

## Step 4: Create `LESSONS_LEARNED.md`

This is the curated knowledge base — validated corrections and reusable insights.
This is where "the agent keeps doing X wrong → do Y instead" lives.
NOT in AGENTS.md. Here.

Categories: Architecture & Design Decisions, Code Patterns & Pitfalls, Testing & Quality, Performance & Infrastructure, Dependencies & External Services, Process & Workflow, plus an Archive section at bottom.

Format: `**[YYYY-MM-DD]** Brief title — Explanation`

---

## Step 5: Create `ITERATION_LOG.md`

Raw, append-only journal. The source of truth for what happened. Patterns here get promoted to LESSONS_LEARNED.

Entry format:
```
### [YYYY-MM-DD] Brief Description
**Context:** What was the goal
**What happened:** Key actions, decisions
**Outcome:** Success / partial / failure
**Insight:** (optional) What would you tell the next agent?
**Promoted to Lessons Learned:** Yes / No
```

---

## Step 6: Git Configuration

```bash
git add AGENTS.md CLAUDE.md LESSONS_LEARNED.md ITERATION_LOG.md .claude/agents/
git commit -m "chore: add AI agent configuration and memory system"
```

---

## Verification Checklist

- [ ] `CLAUDE.md` exists and contains only `Read AGENTS.md`
- [ ] `AGENTS.md` exists, is minimal, contains NO `/init` content and NO corrections (those are in LESSONS_LEARNED)
- [ ] `AGENTS.md` references LESSONS_LEARNED.md, ITERATION_LOG.md, and sub-agents
- [ ] Sub-agents exist in `.claude/agents/` with proper frontmatter
- [ ] `LESSONS_LEARNED.md` exists with categorized sections and Archive
- [ ] `ITERATION_LOG.md` exists with entry format template
- [ ] All files tracked in git
- [ ] No file contains architecture overviews, folder structures, or dependency lists
- [ ] AGENTS.md and LESSONS_LEARNED.md have zero content overlap

---

## Quick Reference: Decision Flowchart

```
Agent keeps making the same mistake?
  └─ Can I fix the codebase to prevent it?
      ├─ YES → Fix the code (better tests, clearer naming, linting)
      └─ NO  → Log in ITERATION_LOG → if 2+ times → promote to LESSONS_LEARNED

Agent needs to know something BEFORE it starts exploring?
  └─ Is it discoverable from the codebase?
      ├─ YES → Don't add it anywhere
      └─ NO  → AGENTS.md "Constraints" section (keep it one line)

Complex multi-step task?
  └─ Invoke planner agent BEFORE writing code

Architecture decision?
  └─ Invoke architect agent → record decision as ADR

Frontend component design?
  └─ Invoke ux-expert agent

Recurring task domain with no specialized agent?
  └─ Invoke agent-creator → it will design one following SkillsBench constraints

New model release?
  └─ Delete AGENTS.md. Test. Re-add only what breaks.
  └─ Review LESSONS_LEARNED — archive anything the new model handles correctly.

AGENTS.md getting long?
  └─ Something is wrong. It should shrink over time, not grow.
  └─ Are corrections in AGENTS.md? Move them to LESSONS_LEARNED.
  └─ Is discoverable info in AGENTS.md? Delete it.

Time for maintenance? (weekly/monthly/yearly/new model)
  └─ Hand this entire document to an agent with:
     "Run the Periodic Maintenance Protocol on this project."
  └─ Review the maintenance report. Approve or adjust flagged items.
```

---

## Periodic Maintenance Protocol

This section is designed as a **standalone task**. Hand this entire document to an agent with the instruction: *"Run the maintenance protocol on this project."* The agent should be able to audit and clean all config files without further guidance.

### When to Run

| Frequency | Trigger |
|-----------|---------|
| **Weekly** (active projects) | High iteration velocity, many ITERATION_LOG entries accumulating |
| **Monthly** (steady projects) | Default cadence for most projects |
| **Per model release** | New model may handle things differently — major cleanup opportunity |
| **Yearly** (dormant projects) | Before resuming work after a long pause |

### The Audit — Step by Step

#### Phase 1: Audit AGENTS.md

Goal: AGENTS.md should be **as small as possible**. Every line must earn its place.

```
For each entry in AGENTS.md "Constraints":
  1. Try to discover this information from the codebase alone
  2. If discoverable → REMOVE from AGENTS.md
  3. If not discoverable → KEEP, but check if it's still accurate
  4. If inaccurate → FIX or REMOVE

For each entry in AGENTS.md "Legacy & Deprecated":
  1. Check if the legacy code/routes/files still exist
  2. If removed → REMOVE from AGENTS.md
  3. If still present → KEEP

Check: Does AGENTS.md contain any corrections/patterns?
  → If yes, MOVE them to LESSONS_LEARNED.md

Check: Does AGENTS.md sub-agents table match actual files in .claude/agents/?
  → Remove rows for agents that no longer exist
  → Add rows for agents that exist but aren't listed
```

#### Phase 2: Audit LESSONS_LEARNED.md

Goal: Every lesson must be **still relevant** and **not duplicated** elsewhere.

```
For each lesson:
  1. Still accurate? → If obsolete → MOVE to Archive with date and reason
  2. Now enforced by codebase? → If enforced → MOVE to Archive
  3. Duplicated in AGENTS.md? → REMOVE from AGENTS.md
  4. Too verbose? → CONDENSE
  5. Duplicates another lesson? → MERGE
```

#### Phase 3: Audit ITERATION_LOG.md

Goal: Patterns should be **promoted**.

```
Scan entries since last maintenance:
  1. Repeated issues (2+ entries) not yet in LESSONS_LEARNED → PROMOTE
  2. Valuable unpromoted insights → Propose promotion

Over 200 entries? → Archive older entries (>6 months) to ITERATION_LOG_ARCHIVE.md
```

#### Phase 4: Audit Sub-Agents (.claude/agents/)

```
For each agent:
  1. Still being invoked? → If unused 3+ months → FLAG for review
  2. References stale tools/patterns? → UPDATE or REMOVE
  3. Over 100 lines? → SPLIT or CONDENSE
  4. Overlaps another agent? → MERGE or clarify boundaries

Recurring tasks without agent coverage? → Propose new agent
```

#### Phase 5: Cross-File Consistency Check

```
Check for duplicated content across files → Keep in one place only
Check all file path references → Still valid?
Check AGENTS.md sub-agents table → Matches .claude/agents/ directory?
```

### Maintenance Report Format

```markdown
# Maintenance Report — [YYYY-MM-DD]

## Summary
- AGENTS.md: [N] entries removed, [N] kept, [N] corrected
- LESSONS_LEARNED.md: [N] archived, [N] condensed, [N] merged, [N] kept
- ITERATION_LOG.md: [N] patterns promoted, [N] entries since last maintenance
- Sub-agents: [N] updated, [N] flagged for review, [N] unchanged

## Changes Made
<!-- List each change with brief rationale -->

## Flagged for Developer Decision
<!-- Things the agent couldn't decide autonomously -->

## Health Score
- AGENTS.md size: [N] lines (target: <30)
- LESSONS_LEARNED.md active entries: [N] (target: <50 per category)
- ITERATION_LOG.md unprocessed entries: [N] (target: 0 patterns unhandled)
- Sub-agents count: [N] (warning if >8)
- Cross-file duplicates found: [N] (target: 0)
```

---

## References

- Röttger et al. (2026). *Evaluating AGENTS.md: Are Repository-Level Context Files Helpful for Coding Agents?* [arxiv.org/abs/2602.11988](https://arxiv.org/abs/2602.11988)
- Li et al. (2026). *SkillsBench: Benchmarking How Well Agent Skills Work Across Diverse Tasks.* [arxiv.org/abs/2602.12670](https://arxiv.org/abs/2602.12670)
