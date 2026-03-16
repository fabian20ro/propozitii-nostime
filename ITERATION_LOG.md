# Iteration Log

> Append-only journal of AI agent work sessions.
> **Add an entry at the end of every iteration.**
> Same issue 2+ times? Promote to `LESSONS_LEARNED.md`.

## Entry Format

---

### [YYYY-MM-DD] Brief Description

**Context:** What was the goal
**What happened:** Key actions, decisions
**Outcome:** Success / partial / failure
**Insight:** (optional) What would you tell the next agent?
**Promoted to Lessons Learned:** Yes / No

---

### 2026-03-16: Agent Config Audit & Template Alignment

**Context:** Audit all agent config files against SETUP_AI_AGENT_CONFIG.md guide and align with templates.
**What happened:**
- Added `work style: telegraph` header to AGENTS.md (was missing)
- Added empty `Legacy & Deprecated` section to AGENTS.md (template requirement)
- Condensed Learning System section to telegraph style
- Verified all 4 core sub-agents (architect, planner, ux-expert, agent-creator) are fully compliant
- Verified CLAUDE.md, LESSONS_LEARNED.md, ITERATION_LOG.md match templates
- Noted code-reviewer.md (189 lines) and romanian-linguist.md (143 lines) exceed 100-line soft limit
**Outcome:** Success
**Insight:** Files were already well-structured from the 2026-02-24 migration. Main gaps were cosmetic: missing telegraph header and Legacy section placeholder.
**Promoted to Lessons Learned:** No

<!-- New entries above this line, most recent first -->

### 2026-02-24: AI Agent Configuration Migration

**Context:** Restructure agent config files to match the SETUP_AI_AGENT_CONFIG.md guide — slim AGENTS.md, categorize LESSONS_LEARNED, create ITERATION_LOG, add guide-template sub-agents, clean up inapplicable React/Next.js references in existing agents.
**What happened:**
- Slimmed AGENTS.md from 145 lines to ~25 (non-discoverable constraints only)
- Categorized LESSONS_LEARNED.md into 7 sections + Archive
- Migrated 6 "Common Pitfalls" entries from AGENTS.md to LESSONS_LEARNED.md
- Created this ITERATION_LOG.md
- Added 4 new sub-agents: architect, planner, agent-creator, ux-expert
- Removed React/Next.js section from code-reviewer.md (inapplicable to vanilla JS project)
- Updated code-simplifier.md and refactor-cleaner.md to remove framework-specific references
- Embedded "How To Add A New Sentence Type" and "How To Change Dictionary/Data Rules" recipes into planner.md
- Saved the setup guide as SETUP_AI_AGENT_CONFIG.md for periodic maintenance
**Outcome:** Success
**Insight:** The existing AGENTS.md had good content but most of it was discoverable from README, docs/CODEMAPS, docs/ONBOARDING, and the codebase itself. The 4 non-discoverable traps (verse delimiter, HTML safety coupling, Flyway prod trap, dual-backend parity) are the only items that genuinely need to be known before exploring.
**Promoted to Lessons Learned:** No
