# AGENTS.md

work style: telegraph; noun-phrases ok; drop grammar; min tokens.

> bootstrap context only. discoverable from codebase → don't put here.
> corrections + patterns → LESSONS_LEARNED.md.

## Constraints

1. **Verse delimiter contract:** Multi-line verses use literal `" / "` as delimiter. `HtmlVerseBreaker` converts to `<br/>`. Breaking this string silently kills line breaks in the frontend.

2. **HTML safety coupling:** Backend intentionally returns `<a>` tags from `DexonlineLinkAdder`. Frontend `sanitizeHtml` in `frontend/app.js` has a strict allowlist + `https://dexonline.ro` href validation. Changes to backend anchor attributes MUST update the frontend sanitizer.

3. **Flyway prod trap:** Production has NO Flyway tracking table and auto-migration is OFF. Apply schema changes via `.github/workflows/database.yml` → `run-migrations`. Always use `IF NOT EXISTS` for DDL. Never edit already-applied migration files; add a new `V*.sql`.

4. **Dual-backend parity:** Every sentence type or decorator change must be applied to BOTH `PhraseResource.kt` (Kotlin/Render) AND `api/all.ts` (TypeScript/Vercel). Both must return the same `/api/all` response shape, dexonline links, and `" / "` delimiters.

5. **Rarity is external:** Never set `rarity_level` in `LoadDictionary` — it is managed by the [word-rarity-classifier](https://github.com/fabian20ro/word-rarity-classifier) project. The loader backs up and restores rarity levels across reloads.

## Legacy & Deprecated

<!-- codebase parts that actively mislead. -->

## Learning System

Every session:
1. start: read `LESSONS_LEARNED.md`
2. during: note surprises
3. end: append `ITERATION_LOG.md`
4. reusable insight? → also add `LESSONS_LEARNED.md`
5. same issue 2+ times in log? → promote to `LESSONS_LEARNED.md`
6. surprise? → flag to developer (they decide: fix codebase / update LESSONS_LEARNED / adjust this file)

| File | Purpose | Write When |
|------|---------|------------|
| `LESSONS_LEARNED.md` | curated wisdom + corrections | reusable insight gained |
| `ITERATION_LOG.md` | raw session journal, append-only | every iteration |

Rules: never delete from ITERATION_LOG. Obsolete lessons → Archive in LESSONS_LEARNED. Date-stamp YYYY-MM-DD. When in doubt: log it.

### Periodic Maintenance
Config files audited periodically via `SETUP_AI_AGENT_CONFIG.md`.
See "Periodic Maintenance Protocol" section.

## Sub-Agents

`.claude/agents/`. Invoke proactively.

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
