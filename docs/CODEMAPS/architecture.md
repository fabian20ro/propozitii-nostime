# Architecture Codemap

Freshness: 2026-02-15

## System Topology

```text
GitHub Pages (frontend static SPA)
  -> primary: Render `/api/all?minRarity=1..5&rarity=1..5`
  -> fallback: Vercel `/api/all?minRarity=1..5&rarity=1..5`
  -> health probe: Render `/q/health`
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
- Vercel fallback API endpoint (external deployment): `https://propozitii-nostime.vercel.app/api/all`

## Request Flow (Primary Path)

1. User clicks `Generează altele` in frontend.
2. `frontend/app.js` first tries `GET https://propozitii-nostime.onrender.com/api/all?minRarity=<1..5>&rarity=<1..5>` with an 8s timeout.
3. If Render fails/times out, frontend falls back to `GET https://propozitii-nostime.vercel.app/api/all?...` and starts background Render health polling.
4. Render path: `PhraseResource.getAll()` internally calls all six endpoint methods.
5. Each endpoint:
- creates provider
- wraps provider in decorators
- returns `SentenceResponse` or field in `AllSentencesResponse`
6. Providers query `WordRepository` for random words under constraints.
   - all runtime selections apply `rarity_level BETWEEN minRarity AND rarity`.
7. `DexonlineLinkAdder` injects `<a href="https://dexonline.ro/definitie/...">` around words.
8. Frontend sanitizes returned HTML and renders into cards.

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

## External Rarity Classification

This repository no longer contains offline rarity classification tooling.
It consumes `words.rarity_level` at runtime for filtering only.

The classifier now lives in:
- https://github.com/fabian20ro/word-rarity-classifier

Overview document:
- `docs/rarity-classification-system.md`

## Deployment Reality

- Backend image is JVM-based (Temurin 21), not native.
- Render deploys using `render.yaml` (Docker runtime).
- Frontend deploys via GitHub Actions Pages workflow.
- Fallback API runs as Vercel `api/all.ts` and must preserve `/api/all` response shape.
- Vercel fallback reads directly from Supabase (`SUPABASE_URL` + `SUPABASE_PUBLISHABLE_KEY`), not from Render.
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
- Fallback API contract: Vercel `/api/all` must forward the same two query params and return the same six keys as strings.

## Where To Extend

- New sentence type:
- new provider
- new endpoint in `PhraseResource`
- optionally new `/api/all` field + frontend card and `FIELD_MAP` entry

- New lexical/data feature:
- migration in `db/migration`
- update loader + repository + tests + docs together
