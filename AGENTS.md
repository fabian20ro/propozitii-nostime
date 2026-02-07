# AGENTS.md

This file is the operating guide for agentic contributors in this repository.

## Mission

Build and maintain a Romanian funny sentence generator with:
- backend: Kotlin + Quarkus REST API
- data: PostgreSQL (`words` table, dictionary-derived)
- frontend: static HTML/CSS/JS on GitHub Pages

Primary quality target: keep sentence generation constraints correct (rhyme, syllables, morphology) while preserving safe HTML rendering and stable API behavior.

## Start Here (10 minutes)

1. Read `README.md` for architecture and local run context.
2. Read codemaps in this order:
   - `docs/CODEMAPS/architecture.md`
   - `docs/CODEMAPS/backend.md`
   - `docs/CODEMAPS/data.md`
   - `docs/CODEMAPS/frontend.md`
3. Read `LESSONS_LEARNED.md` for known blockers and unexpected behavior. Update it when you discover new ones.
4. Run tests before and after your change: `./gradlew test`

## Golden Commands

- Dev server: `./gradlew quarkusDev`
- Tests: `./gradlew test`
- Build: `./gradlew build`
- Coverage report: `./gradlew jacocoTestReport`
- Dictionary setup (one-time/new DB):
  - `./gradlew downloadDictionary`
  - `SUPABASE_DB_URL=... SUPABASE_DB_USER=postgres SUPABASE_DB_PASSWORD=... ./gradlew loadDictionary`

## Codebase Map

- API/resource layer: `src/main/kotlin/scrabble/phrases/PhraseResource.kt`
- Cross-cutting API guards:
  - `src/main/kotlin/scrabble/phrases/RateLimitFilter.kt`
  - `src/main/kotlin/scrabble/phrases/GlobalExceptionMapper.kt`
- Sentence generation strategies: `src/main/kotlin/scrabble/phrases/providers/`
- Post-processing decorators: `src/main/kotlin/scrabble/phrases/decorators/`
- DB access + startup caches: `src/main/kotlin/scrabble/phrases/repository/WordRepository.kt`
- Word model + morphology/syllables: `src/main/kotlin/scrabble/phrases/words/`
- Dictionary loader CLI: `src/main/kotlin/scrabble/phrases/tools/LoadDictionary.kt`
- Frontend app:
  - `frontend/index.html`
  - `frontend/app.js`
  - `frontend/style.css`

## Critical Invariants

1. **Provider output format:**
   - Multi-line verses must use literal delimiter `" / "`.
   - `HtmlVerseBreaker` converts this delimiter to `<br/>`.

2. **HTML safety contract:**
   - Backend intentionally returns HTML anchors from `DexonlineLinkAdder`.
   - Frontend must sanitize before `innerHTML` (`sanitizeHtml` allowlist + strict `https://dexonline.ro` href validation).
   - If you add/change attributes in backend anchors, update frontend sanitizer allowlist.

3. **DB-driven randomness contract:**
   - `WordRepository` caches counts/rhyme groups/prefixes at startup (`@PostConstruct`).
   - Query paths with `exclude` sets use `ORDER BY RANDOM()`; non-exclude paths may use count+offset.
   - Any schema/query semantics changes must be reflected in `initCounts()` and query methods.

4. **Grammar constraints are implementation, not comments:**
   - Haiku expects noun articulated syllables = 5, adjective syllable rules by noun gender, verb syllables = 3.
   - Couplet and Mirror rely on rhyme-group availability and per-request uniqueness sets.
   - Tautogram requires a 2-letter prefix present across noun+adjective+verb.

5. **Flyway policy:**
   - Dev/test: migrations auto-run.
   - Prod (`%prod`): auto-migration is off; schema changes are manual.
   - Never edit already-applied migration files for production history; add a new `V*.sql` migration.

## How To Add A New Sentence Type

1. Add provider in `src/main/kotlin/scrabble/phrases/providers/` implementing `ISentenceProvider`.
2. Add endpoint in `src/main/kotlin/scrabble/phrases/PhraseResource.kt`.
3. Choose decorator chain:
   - sentence: `FirstSentenceLetterCapitalizer -> DexonlineLinkAdder`
   - verse: `VerseLineCapitalizer -> DexonlineLinkAdder -> HtmlVerseBreaker`
4. Extend `/api/all` response contract if the frontend should display it.
5. Update frontend `FIELD_MAP` and add a card in `frontend/index.html`.
6. Add/adjust integration assertions in `src/test/kotlin/scrabble/phrases/PhraseResourceTest.kt`.

## How To Change Dictionary/Data Rules

1. Update morphology/syllables logic in `src/main/kotlin/scrabble/phrases/words/`.
2. Keep loader and runtime aligned:
   - `src/main/kotlin/scrabble/phrases/tools/LoadDictionary.kt`
   - `src/main/kotlin/scrabble/phrases/repository/WordRepository.kt`
3. Add or update migrations in `src/main/resources/db/migration/`.
4. Update test seed in `src/test/resources/db/testmigration/V100__seed_test_data.sql` so provider constraints still hold.
5. Run `./gradlew test`.

## Testing Expectations

Before handing off:
- Run `./gradlew test`
- If behavior/API changed, verify `/api/all` still returns all expected keys
- If HTML output changed, ensure sanitizer still permits needed tags/attrs and blocks unsafe hrefs
- If DB query logic changed, ensure test seed still satisfies rhyme/prefix/syllable constraints

## Deployment Notes

- Backend deploy target is Render via `render.yaml`.
- Frontend deploy target is GitHub Pages via `.github/workflows/frontend.yml`.
- Backend CI is `.github/workflows/backend.yml`.

## Common Pitfalls

- Returning raw unsanitized HTML changes from backend without updating frontend sanitizer.
- Breaking verse delimiter contract (`" / "`) and wondering why line breaks disappear.
- Introducing new provider constraints that test seed data cannot satisfy.
- Updating migration files in-place instead of adding a new versioned migration.
- Forgetting that `/api/all` is the frontend's primary fetch path.

## New Agent Ramp

Use `docs/ONBOARDING.md` for a first-hour workflow and first-safe-change checklist.