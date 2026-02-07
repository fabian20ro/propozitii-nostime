# TODOS

## Goal
Add a configurable "word strangeness" slider so regionalisms/archaisms are much rarer by default, using an LMStudio one-time scoring pipeline and DB-backed filtering.

## Recommended Product Decisions
- Slider name: `Stranietate cuvinte` (0-100).
- Default value: `20` (common words dominate).
- Semantics: higher value allows rarer words; lower value restricts to common words.
- Keep all current sentence-type constraints (rhyme/syllables/morphology) unchanged.

## Important Decision on "Top 5000 Only"
- Do **not** switch generation to only top 5000 words by default.
- Reason: this will likely break rhyme-group/prefix availability used by `Couplet`, `Mirror`, and `Tautogram`.
- Better approach: keep full dictionary and filter by `rarity_score <= slider`.
- Optional fallback only if DB is unavailable: static cached common set, clearly marked degraded mode.

## Phase 1: Schema + Indexing
- [ ] Add Flyway migration `V3__add_word_rarity.sql`:
- `rarity_score SMALLINT NOT NULL DEFAULT 50` (0=most common, 100=most rare)
- optional metadata fields (`rarity_model`, `rarity_version`, `rarity_scored_at`) if auditability is needed.
- [ ] Add indexes for query performance:
- `(type, rarity_score, syllables)`
- `(type, rarity_score, rhyme)`
- `(type, rarity_score, articulated_syllables)`
- `(type, rarity_score, first_letter)`

## Phase 2: One-Time LMStudio Rarity Scoring Tool
- [ ] Add tool in `src/main/kotlin/scrabble/phrases/tools/` (e.g. `ScoreWordRarity.kt`) to:
- read distinct words from DB (or `words.txt`),
- call LMStudio local OpenAI-compatible endpoint in batches,
- write deterministic numeric rarity score 0-100 for each word.
- [ ] Enforce strict JSON output contract from model:
- `{ "word": "...", "rarity": 0..100, "tag": "common|regionalism|archaism|rare|uncertain" }`
- [ ] Add retry/resume support (checkpoint file) for long runs.
- [ ] Store interim output to CSV/JSONL, then bulk-update DB in one transaction.
- [ ] Add dry-run mode (`--limit`) for prompt tuning on first ~200 words.

## Phase 3: Repository + Provider Filtering
- [ ] Add `maxRarity` parameter to repository selection methods (noun/adjective/verb variants).
- [ ] Ensure filtered versions preserve existing uniqueness behavior (`exclude` sets).
- [ ] Update cache initialization to include rarity-aware counts/group candidates.
- [ ] For rhyme/prefix group methods, filter candidates by rarity threshold before selecting groups.
- [ ] Keep current fallback behavior, but return clear error if constraints are impossible under very low threshold.

## Phase 4: API Contract
- [ ] Add optional query param `strangeness` (`0..100`, default `20`) to:
- `/api/all` and all single-sentence endpoints.
- [ ] Validate/clamp invalid values server-side.
- [ ] Propagate to providers through repository calls.
- [ ] Keep backward compatibility: missing param must behave like today but with default `20`.

## Phase 5: Frontend Slider
- [ ] Add slider UI in `frontend/index.html` near refresh action.
- [ ] Persist value in `localStorage`.
- [ ] Send value in request query string:
- `/api/all?strangeness=<value>`.
- [ ] Add readable label text for accessibility (e.g. `Foarte comun` <-> `Foarte rar`).
- [ ] Keep sanitizer/CSP unchanged unless new HTML attributes are introduced.

## Phase 6: Test/Data Updates
- [ ] Update `src/test/resources/db/testmigration/V100__seed_test_data.sql` with `rarity_score` values.
- [ ] Extend integration tests to assert rarity compliance per threshold.
- [ ] Add unit tests for:
- slider param validation/clamping,
- repository rarity filters with and without `exclude`,
- rhyme/prefix availability under thresholds.
- [ ] Maintain existing invariant tests for verse delimiter and safe HTML output.

## Rollout Plan
- [ ] Run one-time scoring on full dictionary locally with LMStudio.
- [ ] Review outliers manually (sample 200 words across bins).
- [ ] Upload scores to Supabase.
- [ ] Deploy backend with threshold filtering first (default conservative).
- [ ] Deploy frontend slider after backend is live.

## Risks to Watch
- Very low thresholds can make some providers unsatisfiable (especially ABBA rhyme and tautogram).
- Model inconsistency across runs; pin model name/version and keep outputs.
- Query cost increase if rarity filters are added without proper indexes/caches.

