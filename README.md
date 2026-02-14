# Propozitii Nostime

[![Build Backend](https://github.com/fabian20ro/propozitii-nostime/actions/workflows/backend.yml/badge.svg)](https://github.com/fabian20ro/propozitii-nostime/actions/workflows/backend.yml)
[![Deploy Frontend](https://github.com/fabian20ro/propozitii-nostime/actions/workflows/frontend.yml/badge.svg)](https://github.com/fabian20ro/propozitii-nostime/actions/workflows/frontend.yml)

Generator de propozitii hazoase in limba romana (Romanian funny sentence generator).

**Live Demo:** https://fabian20ro.github.io/propozitii-nostime/

## Architecture

| Component | Technology | Hosting |
|-----------|------------|---------|
| Backend | Kotlin + Quarkus 3.17 (JVM) | [Render.com](https://propozitii-nostime.onrender.com/q/health) |
| Fallback API | Vercel Serverless Function (`api/all.ts`) | [Vercel](https://propozitii-nostime.vercel.app/api/all) |
| Frontend | Static HTML/CSS/JS | [GitHub Pages](https://fabian20ro.github.io/propozitii-nostime/) |
| Database | PostgreSQL ([Supabase](https://supabase.com)) | Supabase Free Tier |
| Dictionary | [dexonline.ro](https://dexonline.ro) Scrabble word list | Loaded into Supabase |

### Zero-cost stack

All services run on free tiers: Render.com (backend), Vercel (fallback API lambda), Supabase (database), and GitHub Pages (frontend).

The Romanian Scrabble dictionary (~80K words) is stored in Supabase PostgreSQL with indexed columns for type, rhyme, syllable count, and first letter. Each API request queries the database directly — no in-memory dictionary, no mutable state, no reset needed.

The backend runs as a JVM uber-jar on Render.com free tier. Cold starts may take up to 60 seconds; the frontend health-polls and shows a loading message until the backend is ready.
For user-facing cold starts, the frontend also has a Vercel fallback API (`FALLBACK_API_BASE` in `frontend/app.js`) implemented in `api/all.ts`. The fallback generates the same `/api/all` response directly from Supabase.

## API Endpoints

- `GET /api/all` — All 6 sentence types in a single response
- `GET /api/haiku` — Haiku-style sentence (5-7-5 syllables)
- `GET /api/distih` — Distih (2 lines, noun-adj-verb-noun-adj pattern)
- `GET /api/comparison` — Comparison sentence ("X e mai adj decât Y")
- `GET /api/definition` — Dictionary-style definition
- `GET /api/tautogram` — All words start with same two-letter prefix
- `GET /api/mirror` — ABBA rhyme scheme (4 lines)
- `GET /q/health` — Health check

All sentence endpoints accept optional query parameter `rarity=1..5` (default `2`):
- `1` = very common words
- `5` = very rare/archaic/regional words

## Vercel Lambda Fallback Contract

Frontend behavior in `frontend/app.js`:
- primary: `API_BASE = https://propozitii-nostime.onrender.com/api`
- fallback: `FALLBACK_API_BASE = https://propozitii-nostime.vercel.app/api`
- health polling: `GET https://propozitii-nostime.onrender.com/q/health`

Required fallback endpoint contract:
- `GET /api/all?minRarity=1..5&rarity=1..5`
- returns JSON with all keys as strings: `haiku`, `distih`, `comparison`, `definition`, `tautogram`, `mirror`
- supports CORS for `https://fabian20ro.github.io` (or `*`) because frontend is hosted on GitHub Pages
- preserves backend HTML contract (dexonline anchors + verse `<br/>`)

Required Vercel env vars for `api/all.ts`:
- `SUPABASE_URL=https://<project-ref>.supabase.co`
- `SUPABASE_PUBLISHABLE_KEY=<sb_publishable_...>` (preferred)

Optional:
- `ALLOWED_ORIGINS=https://fabian20ro.github.io,https://<your-other-origin>`
- `ALLOW_SUPABASE_SERVICE_ROLE_FALLBACK=true` only for explicit emergency fallback

## Local Development

### Prerequisites

- Java 21 (e.g., `brew install --cask temurin@21`)
- Gradle 8.11 (wrapper included)
- Docker (for running integration tests via Testcontainers)

### First-time setup

```bash
# Enable credential-scanning pre-commit hook
git config core.hooksPath .githooks
```

### Environment variables (production-like runs only)

```bash
export SUPABASE_DB_URL=jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres
export SUPABASE_DB_USER=postgres
export SUPABASE_DB_PASSWORD=<your-password>
```

In local dev/test, Quarkus Dev Services auto-provisions PostgreSQL via Docker, so these variables are usually not required.

### Loading the dictionary

```bash
# Download dictionary file (once)
./gradlew downloadDictionary

# Load dictionary into a target PostgreSQL (one-time setup per DB)
./gradlew loadDictionary
```

### Rarity Classification Scope

This repository no longer includes the offline rarity classification pipeline.
It only consumes `words.rarity_level` at runtime for sentence filtering (`minRarity`/`rarity` query params).

The classification system now lives in a separate project:
- https://github.com/fabian20ro/word-rarity-classifier

For a system-level overview and lessons learned from classification campaigns, see:
- `docs/rarity-classification-system.md`

### Running locally

```bash
# Start Quarkus dev server
./gradlew quarkusDev

# API available at http://localhost:8080
```

### Running tests

```bash
./gradlew test
```

`./gradlew test` now also runs the Vercel fallback unit tests from `api/__tests__/all.test.ts` (via `npm test`) before JVM tests.

## Developer Onboarding Docs

- Agent operating guide: `AGENTS.md`
- New contributor ramp-up: `docs/ONBOARDING.md`
- Codemap index: `docs/CODEMAPS/README.md`
- Vercel fallback lambda guide: `docs/VERCEL_LAMBDA.md`

### Building

```bash
./gradlew build
```

## Project Structure

```
propozitii-nostime/
├── src/main/kotlin/scrabble/phrases/
│   ├── PhraseResource.kt           # REST API endpoints
│   ├── SentenceResponse.kt         # JSON response
│   ├── words/                       # Word types (sealed interface + data classes)
│   ├── repository/                  # WordRepository (SQL queries to Supabase)
│   ├── providers/                   # Sentence generators (6 types)
│   ├── decorators/                  # Sentence decorators (links, formatting)
│   └── tools/                       # LoadDictionary entrypoint
├── frontend/                        # Static frontend for GitHub Pages
├── Dockerfile                       # JVM uber-jar multi-stage build
├── render.yaml                      # Render deployment config
└── .github/workflows/               # CI/CD pipelines
```

## Database Schema

Schema is defined by [Flyway](https://flywaydb.org/) migrations in `src/main/resources/db/migration/`. Flyway runs automatically in dev/test but is **disabled in production** (`%prod.quarkus.flyway.migrate-at-start=false`). Schema changes must be applied manually to Supabase.

Indexes on `(type)`, `(type, syllables)`, `(type, first_letter)`, `(type, rhyme, syllables)`, `(type, articulated_syllables)`, `(type, rarity_level, syllables)`, `(type, rarity_level, rhyme)`, `(type, rarity_level, articulated_syllables)`, and `(type, rarity_level, first_letter)`.

## Acknowledgements

The Romanian word dictionary used in this project comes from [dexonline.ro](https://dexonline.ro), an exceptional open-source Romanian dictionary project. Many thanks to the dexonline team for maintaining such a comprehensive and freely available linguistic resource. Clicking any word in a sentence opens a draggable bottom sheet drawer with its dexonline.ro definition. The drawer can be resized by dragging the header, and the chosen height persists across sessions.

## License

GPL-3.0
