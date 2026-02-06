# Architecture Codemap

Freshness: 2026-02-06

## System Topology

```text
GitHub Pages (frontend static SPA)
  -> HTTPS GET /api/all, /q/health
Render.com (Quarkus JVM backend)
  -> JDBC
Supabase PostgreSQL (words dictionary table)
```

## Runtime Components

- Frontend: `frontend/`
- Backend API: `src/main/kotlin/scrabble/phrases/`
- Migrations: `src/main/resources/db/migration/`
- Deployment config:
- `render.yaml`
- `Dockerfile`

## Request Flow (Primary Path)

1. User clicks `Generează altele` in frontend.
2. `frontend/app.js` calls `GET https://propozitii-nostime.onrender.com/api/all`.
3. `PhraseResource.getAll()` internally calls all six endpoint methods.
4. Each endpoint:
- creates provider
- wraps provider in decorators
- returns `SentenceResponse` or field in `AllSentencesResponse`
5. Providers query `WordRepository` for random words under constraints.
6. `DexonlineLinkAdder` injects `<a href="https://dexonline.ro/definitie/...">` around words.
7. Frontend sanitizes returned HTML and renders into cards.

## Cross-Cutting Backend Behaviors

- Rate limit: `RateLimitFilter` applies to `api/*`, max 30 requests/minute/IP (`X-Forwarded-For` first hop, fallback `unknown`).
- Exception shielding: `GlobalExceptionMapper` returns generic 500 JSON body.
- CORS: configured for GitHub Pages origin + dev origins in `%dev`.

## Startup and Data Access Model

`WordRepository` is `@Startup` + `@ApplicationScoped` and preloads:
- counts by type
- counts by `(type, syllables)`
- counts by `(type, articulated_syllables)`
- noun/verb rhyme groups
- valid two-letter prefixes with all three parts of speech

These caches support fast random-offset queries and avoid repeated group scans.

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
- Frontend relies primarily on `/api/all` keys: `haiku`, `couplet`, `comparison`, `definition`, `tautogram`, `mirror`.

## Where To Extend

- New sentence type:
- new provider
- new endpoint in `PhraseResource`
- optionally new `/api/all` field + frontend card and `FIELD_MAP` entry

- New lexical/data feature:
- migration in `db/migration`
- update loader + repository + tests + docs together
