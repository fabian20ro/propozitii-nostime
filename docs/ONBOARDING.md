# New Agent Onboarding

This guide gets a new agentic contributor productive in about 60 minutes.

## 0. Goal and System Shape (5 min)

You are working on a sentence-generation system with a strict split:
- backend generates constrained Romanian sentence templates and returns HTML-safe links
- frontend renders and sanitizes backend HTML
- PostgreSQL stores dictionary-derived words and precomputed morphology metadata

Read first:
- `README.md`
- `AGENTS.md`
- `docs/rarity-runbook.md` (if your change touches rarity Step 2/3/4/5 behavior)

## 1. Run Locally (10 min)

Prerequisites:
- Java 21
- Docker (for tests/dev DB via Quarkus Dev Services)

Commands:
```bash
./gradlew quarkusDev
./gradlew test
```

Notes:
- Dev/test profile auto-provisions PostgreSQL containers.
- Production DB vars (`SUPABASE_DB_*`) are not required for routine local coding.

## 2. Read the Core Flow (15 min)

Follow this exact path:
1. `src/main/kotlin/scrabble/phrases/PhraseResource.kt`
2. `src/main/kotlin/scrabble/phrases/providers/`
3. `src/main/kotlin/scrabble/phrases/decorators/`
4. `src/main/kotlin/scrabble/phrases/repository/WordRepository.kt`
5. `src/main/kotlin/scrabble/phrases/words/`
6. `src/main/kotlin/scrabble/phrases/tools/rarity/`
7. `frontend/app.js`

Mental model:
- provider = language logic
- decorator = formatting/linking
- repository = DB random selection + constraints
- rarity tools = offline scoring workflow (CSV-first; DB write only on upload)
- frontend = `/api/all` fetch, sanitize, render

## 3. Understand Data Guarantees (10 min)

Schema/migrations:
- `src/main/resources/db/migration/V1__create_words_table.sql`
- `src/main/resources/db/migration/V2__add_articulated_syllables.sql`

Seeded integration test data:
- `src/test/resources/db/testmigration/V100__seed_test_data.sql`

Why this matters:
- Providers are constraint-heavy; tests pass only if seed data supports rhyme/prefix/syllable assumptions.

## 4. First Safe Changes (15 min)

Pick one:
1. Add a small provider unit test in decorators/words packages.
2. Improve error copy/frontend states without changing API shape.
3. Add/clarify docs for a proven behavior from code.

Avoid for first change:
- schema changes
- sanitizer contract changes
- flyway production process changes

## 5. Definition of Done

Always before handoff:
```bash
./gradlew test
```

If your change touches rarity tooling:
```bash
./gradlew test --tests 'scrabble.phrases.tools.rarity.*'
```

Then manually verify:
- `/api/all` returns valid keys
- `/api/all` accepts `rarity=1..5` and clamps invalid values
- frontend still renders links and line breaks
- no unsanitized HTML path introduced

## 6. Fast Troubleshooting

- `IllegalStateException` from providers often means data constraints are impossible with current DB/seed data.
- If links disappear in UI, check `sanitizeHtml` allowlist/href validation in `frontend/app.js`.
- If verse cards become one line, check provider delimiter `" / "` and `HtmlVerseBreaker` usage.
- If tests fail with container issues, verify Docker availability.
