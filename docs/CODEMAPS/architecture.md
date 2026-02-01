# Architecture Codemap

> Freshness: 2026-02-01 | Version: 3.0.0

## System Overview

```
Frontend (GitHub Pages)  --HTTPS/CORS-->  Backend (Render.com)  --JDBC-->  Database (Supabase PostgreSQL)
Static HTML/CSS/JS                        Kotlin + Quarkus 3.17              ~80K Romanian words
                                          JVM (eclipse-temurin:21)           Manually-managed schema
```

## Zero-Cost Stack

| Component | Technology | Hosting |
|-----------|------------|---------|
| Backend | Kotlin + Quarkus 3.17, JVM (eclipse-temurin:21) | Render.com free tier |
| Frontend | Static HTML/CSS/JS | GitHub Pages |
| Database | PostgreSQL | Supabase free tier |
| Dictionary | Romanian Scrabble dictionary (~80K words) | Loaded into Supabase |

## Request Flow

1. Frontend `refresh()` calls `/api/all` (single batch endpoint returning all 6 sentence types)
2. Each sentence type constructs: `Provider -> Decorator chain -> SentenceResponse`
3. Provider queries `WordRepository` (random words from PostgreSQL, with exclusion sets to prevent duplicate words)
4. Decorators: capitalize (sentence or per-verse-line), add dexonline links, break verses
5. Frontend sanitizes HTML, renders all 6 sentences into DOM

## Deployment

- **Backend**: Docker JVM image auto-deployed via `render.yaml` on push to master
- **Frontend**: Separate GitHub Actions workflow deploys `frontend/` to GitHub Pages
- **Database**: Flyway migrations disabled in production (`%prod.quarkus.flyway.migrate-at-start=false`); schema changes must be applied manually to Supabase. Flyway runs only in dev/test. Dictionary loaded once via `./gradlew loadDictionary`

## Key Design Decisions

- Stateless backend: no in-memory state, every request queries PostgreSQL
- JVM build with uber-jar: simpler build, compatible with Render.com free tier
- Decorator pattern for sentence post-processing
- Frontend handles Render.com cold starts with health polling (up to 60s)
- `%prod` profile for DB credentials; dev/test use Testcontainers via Quarkus Dev Services

## Security Hardening

- **CSP**: `object-src 'none'`, `base-uri 'self'`, `form-action 'none'` block plugin injection, base-tag hijacking, and form exfiltration
- **Sanitizer**: href attributes validated via `URL` parsing; only `https://dexonline.ro` origins are allowed
- **Iframe**: `sandbox="allow-scripts allow-same-origin"` restricts the dexonline.ro embed to script execution and same-origin access only

## Dependency Graph

```
PhraseResource
  +-- WordRepository
  +-- 6 Providers (each use WordRepository)
  +-- 4 Decorators (chain wrapping ISentenceProvider)
  +-- SentenceResponse, AllSentencesResponse

WordRepository
  +-- Noun, Adjective, Verb, NounGender, WordUtils

LoadDictionary (standalone CLI tool)
  +-- Noun, Adjective, Verb, NounGender, WordUtils
```
