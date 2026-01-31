# Architecture Codemap

> Freshness: 2026-01-31 | Version: 3.0.0

## System Overview

```
Frontend (GitHub Pages)  --HTTPS/CORS-->  Backend (Render)  --JDBC-->  Database (Supabase PostgreSQL)
Static HTML/CSS/JS                        Kotlin + Quarkus 3.17              ~80K Romanian words
                                          JVM (eclipse-temurin:21)           Manually-managed schema
```

## Zero-Cost Stack

| Component | Technology | Hosting |
|-----------|------------|---------|
| Backend | Kotlin + Quarkus 3.17, JVM (eclipse-temurin:21) | Render free tier |
| Frontend | Static HTML/CSS/JS | GitHub Pages |
| Database | PostgreSQL | Supabase free tier |
| Dictionary | dexonline.ro Scrabble word list | Loaded into Supabase |

## Request Flow

1. Frontend `refresh()` calls all 6 `/api/*` endpoints in parallel
2. Each endpoint constructs: `Provider -> Decorator chain -> SentenceResponse`
3. Provider queries `WordRepository` (random words from PostgreSQL)
4. Decorators: capitalize, add dexonline links, break verses
5. Frontend sanitizes HTML, renders into DOM

## Deployment

- **Backend**: Docker JVM image auto-deployed via `render.yaml` on push to master
- **Frontend**: Separate GitHub Actions workflow deploys `frontend/` to GitHub Pages
- **Database**: Flyway migrations disabled in production (`%prod.quarkus.flyway.migrate-at-start=false`); schema changes must be applied manually to Supabase. Flyway runs only in dev/test. Dictionary loaded once via `./gradlew loadDictionary`

## Key Design Decisions

- Stateless backend: no in-memory state, every request queries PostgreSQL
- JVM build with uber-jar: simpler build, compatible with Render free tier
- Decorator pattern for sentence post-processing
- Frontend handles Render cold starts with health polling (up to 60s)
- `%prod` profile for DB credentials; dev/test use Testcontainers via Quarkus Dev Services

## Dependency Graph

```
PhraseResource
  +-- WordRepository
  +-- 6 Providers (each use WordRepository)
  +-- 3 Decorators (chain wrapping ISentenceProvider)
  +-- SentenceResponse

WordRepository
  +-- Noun, Adjective, Verb, NounGender, WordUtils

LoadDictionary (standalone CLI tool)
  +-- Noun, Adjective, Verb, NounGender, WordUtils
```
