# AGENTS.md

> This file provides non-discoverable bootstrap context.
> If the model can find it in the codebase, it does not belong here.
> For corrections and patterns, see LESSONS_LEARNED.md.

## Constraints

1. **Verse delimiter contract:** Multi-line verses use literal `" / "` as delimiter. `HtmlVerseBreaker` converts to `<br/>`. Breaking this string silently kills line breaks in the frontend.

2. **HTML safety coupling:** Backend intentionally returns `<a>` tags from `DexonlineLinkAdder`. Frontend `sanitizeHtml` in `frontend/app.js` has a strict allowlist + `https://dexonline.ro` href validation. Changes to backend anchor attributes MUST update the frontend sanitizer.

3. **Flyway prod trap:** Production has NO Flyway tracking table and auto-migration is OFF. Apply schema changes via `.github/workflows/database.yml` → `run-migrations`. Always use `IF NOT EXISTS` for DDL. Never edit already-applied migration files; add a new `V*.sql`.

4. **Dual-backend parity:** Every sentence type or decorator change must be applied to BOTH `PhraseResource.kt` (Kotlin/Render) AND `api/all.ts` (TypeScript/Vercel). Both must return the same `/api/all` response shape, dexonline links, and `" / "` delimiters.

5. **Rarity is external:** Never set `rarity_level` in `LoadDictionary` — it is managed by the [word-rarity-classifier](https://github.com/fabian20ro/word-rarity-classifier) project. The loader backs up and restores rarity levels across reloads.

## Learning System

This project uses a persistent learning system. Follow this workflow every session:

1. **Start of task:** Read `LESSONS_LEARNED.md` — it contains validated corrections and patterns
2. **During work:** Note any surprises or non-obvious discoveries
3. **End of iteration:** Append to `ITERATION_LOG.md` with what happened
4. **If insight is reusable and validated:** Also add to `LESSONS_LEARNED.md`
5. **If same issue appears 2+ times in log:** Promote to `LESSONS_LEARNED.md`
6. **If something surprised you:** Flag it to the developer

| File | Purpose | When to Write |
|------|---------|---------------|
| `LESSONS_LEARNED.md` | Curated, validated wisdom and corrections | When insight is reusable |
| `ITERATION_LOG.md` | Raw session journal (append-only, never delete) | Every iteration (always) |

### Periodic Maintenance
This project's config files are audited periodically using `SETUP_AI_AGENT_CONFIG.md`.

## Sub-Agents

Specialized agents in `.claude/agents/`. Invoke proactively — don't wait to be asked.

| Agent | File | Invoke When |
|-------|------|-------------|
| Architect | `.claude/agents/architect.md` | System design, scalability, refactoring, ADRs |
| Planner | `.claude/agents/planner.md` | Complex multi-step features — plan before coding |
| UX Expert | `.claude/agents/ux-expert.md` | UI components, interaction patterns, accessibility |
| Agent Creator | `.claude/agents/agent-creator.md` | Need a new specialized agent for a recurring task domain |
| Code Reviewer | `.claude/agents/code-reviewer.md` | Code quality, security review after changes |
| Code Simplifier | `.claude/agents/code-simplifier.md` | Clarity and maintainability refinement |
| Refactor Cleaner | `.claude/agents/refactor-cleaner.md` | Dead code removal, dependency cleanup |
| Romanian Linguist | `.claude/agents/romanian-linguist.md` | Adjective feminization, syllable counting, morphology |
