# Architecture Codemap

Freshness: 2026-02-12

## System Topology

```text
GitHub Pages (frontend static SPA)
  -> HTTPS GET /api/all?minRarity=1..5&rarity=1..5, /q/health
Render.com (Quarkus JVM backend)
  -> JDBC
Supabase PostgreSQL (words dictionary table)
```

## Runtime Components

- Frontend: `frontend/`
- Backend endpoint code: `src/main/kotlin/scrabble/phrases/`
- Migrations: `src/main/resources/db/migration/`
- Deployment config:
- `render.yaml`
- `Dockerfile`

## Request Flow (Primary Path)

1. User clicks `Generează altele` in frontend.
2. `frontend/app.js` calls `GET https://propozitii-nostime.onrender.com/api/all?minRarity=<1..5>&rarity=<1..5>`.
3. `PhraseResource.getAll()` internally calls all six endpoint methods.
4. Each endpoint:
- creates provider
- wraps provider in decorators
- returns `SentenceResponse` or field in `AllSentencesResponse`
5. Providers query `WordRepository` for random words under constraints.
   - all runtime selections apply `rarity_level BETWEEN minRarity AND rarity`.
6. `DexonlineLinkAdder` injects `<a href="https://dexonline.ro/definitie/...">` around words.
7. Frontend sanitizes returned HTML and renders into cards.

## Cross-Cutting Backend Behaviors

- Rate limit: `RateLimitFilter` applies to `api/*`, max 30 requests/minute/IP (`X-Forwarded-For` first hop, fallback `unknown`).
- Exception shielding: `GlobalExceptionMapper` returns generic 500 JSON body.
- CORS: configured for GitHub Pages origin + dev origins in `%dev`.
- Constraint fallback: providers can throw `IllegalStateException`; `PhraseResource` converts this to an HTTP 200 placeholder sentence.

## Startup and Data Access Model

`WordRepository` is `@Startup` + `@ApplicationScoped` and preloads:
- counts by type
- counts by `(type, syllables)`
- counts by `(type, articulated_syllables)`
- noun/verb rhyme groups
- valid two-letter prefixes with all three parts of speech

These caches support fast random-offset queries and avoid repeated group scans.

## Offline Rarity Pipeline

`RarityPipeline` (`src/main/kotlin/scrabble/phrases/tools/RarityPipeline.kt`) delegates to modular steps in `src/main/kotlin/scrabble/phrases/tools/rarity/`:
- Step 1: DB export -> `build/rarity/step1_words.csv`
- Step 2: LMStudio scoring -> `build/rarity/runs/<run>.csv` + JSONL logs
- Step 3: local compare -> `build/rarity/step3_comparison.csv`
- Step 4: upload final levels -> `words.rarity_level`
- Step 5 (optional): LM rebalance on Step2/Step3 CSV -> rebalanced local CSV (`final_level`)
- Step 2 internals are split by concern: `LmStudioClient` (orchestration), `LmStudioRequestSupport` (payload assembly + `LmStudioErrorClassifier`), `LmStudioResponseParser` (lenient parse/matching), `LmStudioHttpGateway` (transport/endpoints), plus model-config registry/constants for per-model defaults

Operational safeguards:
- exclusive file lock per run CSV (`<run>.csv.lock`)
- guarded final rewrite (abort on shrink)
- strict CSV parsing (malformed rows fail fast)
- recursion depth guard (max 10) on batch split/retry
- step state trace in `build/rarity/runs/<run>.state.json`
- step5 traces in `build/rarity/rebalance/runs/<run>.jsonl` and `build/rarity/rebalance/failed_batches/<run>.failed.jsonl`
- run-scoped fallback when `response_format` is unsupported
- run-scoped fallback when model-specific reasoning controls are unsupported
- `word_id`-first parsing + in-process retry for unresolved partial LM outputs
- Step 3 supports a third run CSV (`--run-c-csv`) and configurable merge strategy (`--merge-strategy median|any-extremes`)
- Step 3 `median()` uses `Math.round()` (half-up rounding), not Kotlin `roundToInt()` (banker's rounding)

## Deployment Reality

- Backend image is JVM-based (Temurin 21), not native.
- Render deploys using `render.yaml` (Docker runtime).
- Frontend deploys separately via GitHub Actions Pages workflow.
- Flyway is on by default in dev/test and explicitly off in `%prod`.

## Critical Contracts Between Layers

1. Verse delimiter contract:
- Providers use literal `" / "` between lines.
- Decorator converts to `<br/>` for display.

2. HTML contract:
- Backend intentionally returns anchor tags.
- Frontend must sanitize before `innerHTML`.

3. API contract:
- Frontend relies primarily on `/api/all` keys: `haiku`, `distih`, `comparison`, `definition`, `tautogram`, `mirror`.
- Rarity control contract: `rarity=1..5` (default `2`, max) and `minRarity=1..5` (default `1`, min) across all sentence endpoints. Backend-compatible: `minRarity` is optional.

## Where To Extend

- New sentence type:
- new provider
- new endpoint in `PhraseResource`
- optionally new `/api/all` field + frontend card and `FIELD_MAP` entry

- New lexical/data feature:
- migration in `db/migration`
- update loader + repository + tests + docs together
