# AGENTS.md

System-instruction index for this repository. Keep this file short; detailed notes belong in linked docs.

## Boot Sequence (activate every session)
1. Read `CLAUDE.md`.
2. Read this file (`AGENTS.md`).
3. Read `LESSONS_LEARNED.md` for blockers/unexpected behavior, update it immediately after you have a revelation of how an unexpected behavior works.

## Updates and Blockers
- Log concise session findings in `LESSONS_LEARNED.md`.

## Activation Map

### When starting work (always)
- `README.md`
- `docs/CODEMAPS/architecture.md`
- `docs/CODEMAPS/backend.md`
- `docs/CODEMAPS/data.md`
- `docs/CODEMAPS/frontend.md`

### When changing backend/API behavior
- `src/main/kotlin/scrabble/phrases/PhraseResource.kt`
- `src/main/kotlin/scrabble/phrases/providers/`
- `src/main/kotlin/scrabble/phrases/decorators/`
- `src/main/kotlin/scrabble/phrases/repository/WordRepository.kt`
- `src/test/kotlin/scrabble/phrases/PhraseResourceTest.kt`

### When changing dictionary/data rules
- `src/main/kotlin/scrabble/phrases/words/`
- `src/main/kotlin/scrabble/phrases/tools/LoadDictionary.kt`
- `src/main/resources/db/migration/`
- `src/test/resources/db/testmigration/V100__seed_test_data.sql`

### When changing frontend rendering/security
- `frontend/index.html`
- `frontend/app.js`
- `frontend/style.css`

## Required Checks (activate before handoff)
- Run `./gradlew test`.
- Verify `/api/all` response keys still match frontend mapping.
- If backend anchor HTML changes, verify frontend sanitizer rules still allow only safe content.
