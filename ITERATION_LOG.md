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

### 2026-05-12: Render/Vercel Smoke Parity Command

**Context:** Add a one-command smoke check for Render primary vs Vercel fallback `/api/all` parity.
**What happened:**
- Added `scripts/smoke-parity.mjs` to fetch both `/api/all?minRarity=1&rarity=2` endpoints
- Added `npm run smoke:parity` in `package.json`
- Documented the command in `docs/RUNBOOK.md`
- Marked the repo TODO complete
- Verified the script with a live run; the Render primary needed a much longer timeout than the first draft, so the default is now 65s and configurable
**Outcome:** Success
**Insight:** Render cold starts can exceed a short smoke timeout; parity checks should default to a generous limit and stay env-overridable.
**Promoted to Lessons Learned:** Yes

### 2026-05-12: Frontend Render Health Poll Diagnostics

**Context:** Improve the frontend experience when Render stays cold or unreachable and the app falls back to Vercel.
**What happened:**
- Added a small `renderColdMessage()` helper in `frontend/app.js`
- Kept the existing immediate fallback notice, but made the background health probe emit a retry-counted message after exhausting `MAX_RETRIES`
- Added a console warning for the exhausted-probe case so the failure mode is visible in logs too
- Marked the matching TODO complete
- Verified the script with `node --check frontend/app.js` and the repo test suite with `npm test`
**Outcome:** Success
**Insight:** When a fallback path is healthy enough to ship traffic, diagnostics should distinguish the first fallback from an exhausted recovery loop; otherwise the user sees the same generic status even when the primary backend never recovers.
**Promoted to Lessons Learned:** Yes

### 2026-05-12: Supabase Query Status Matrix

**Context:** Make `/docs/RUNBOOK.md` easier to use when Supabase-backed requests fail in different ways.
**What happened:**
- Added a small status-code matrix to `docs/RUNBOOK.md` covering `500` and `429` cases
- Kept the existing database-connection troubleshooting section and made the first checks more explicit
- Marked the matching TODO complete
- Added a reusable lesson about status-specific troubleshooting to `LESSONS_LEARNED.md`
**Outcome:** Success
**Insight:** Small runbooks work better when they map status codes to the first next action instead of describing all failures as generic backend errors.
**Promoted to Lessons Learned:** Yes

### 2026-05-13: Frontend health poll cleanup

**Context:** Small cold-start polish for the frontend fallback path.
**What happened:**
- Made `checkHealth()` and `fetchFrom()` clear their abort timers in `finally` so failed requests do not leave stray timers behind
- Skipped the final useless sleep in the background Render warmup loop
- Updated `docs/CODEMAPS/frontend.md` to describe the retry cadence and persistent-cold diagnostic
- Verified with `node --check frontend/app.js`, `npm install` to restore the missing Rollup optional dependency, and `npm test`
**Outcome:** Success
**Insight:** A retry loop should be explicit about its last iteration; otherwise the final failure pays an unnecessary sleep cost and diagnostics arrive later than needed.
**Promoted to Lessons Learned:** Yes

### 2026-05-13: README cold-start timeout sync

**Context:** Keep user-facing cold-start guidance aligned with the smoke-parity script default.
**What happened:**
- Updated `README.md` to say Render cold starts may take up to 65 seconds, matching `scripts/smoke-parity.mjs`
- Verified the script default is `SMOKE_TIMEOUT_MS=65000` and the README line now mirrors that contract
**Outcome:** Success
**Insight:** When a timeout is part of the documented user contract, mirror the executable default exactly so docs do not drift behind the script.
**Promoted to Lessons Learned:** Yes

### 2026-05-14: Runbook cold-start wording sync

**Context:** Keep the operations runbook aligned with the current frontend fallback timing.
**What happened:**
- Updated `docs/RUNBOOK.md` to describe the actual cold-start path: 8s Render fetch timeout, 1.2s fallback hedge delay, and 12x5s background health polling
- Kept the rest of the runbook guidance unchanged
**Outcome:** Success
**Insight:** User-facing runbooks should describe the visible fallback behavior, not just the backend retry window, so operators can reason about what users see during cold starts.
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
