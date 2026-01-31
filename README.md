# Propozitii Nostime

[![Build Backend](https://github.com/fabian20ro/propozitii-nostime/actions/workflows/backend.yml/badge.svg)](https://github.com/fabian20ro/propozitii-nostime/actions/workflows/backend.yml)
[![Deploy Frontend](https://github.com/fabian20ro/propozitii-nostime/actions/workflows/frontend.yml/badge.svg)](https://github.com/fabian20ro/propozitii-nostime/actions/workflows/frontend.yml)

Generator de propozitii hazoase in limba romana (Romanian funny sentence generator).

**Live Demo:** https://fabian20ro.github.io/propozitii-nostime/

## Architecture

| Component | Technology | Hosting |
|-----------|------------|---------|
| Backend | Kotlin + Quarkus 3.17 (GraalVM Native) | [Render](https://propozitii-nostime.onrender.com/q/health) |
| Frontend | Static HTML/CSS/JS | [GitHub Pages](https://fabian20ro.github.io/propozitii-nostime/) |
| Database | PostgreSQL ([Supabase](https://supabase.com)) | Supabase Free Tier |
| Dictionary | [dexonline.ro](https://dexonline.ro) Scrabble word list | Loaded into Supabase |

### Zero-cost stack

All three services run on free tiers: Render (backend), Supabase (database), and GitHub Pages (frontend).

The dictionary (~80K Romanian words) is stored in Supabase PostgreSQL with indexed columns for type, rhyme, syllable count, and first letter. Each API request queries the database directly — no in-memory dictionary, no mutable state, no reset needed.

GraalVM native image compiles the application ahead-of-time, resulting in:
- Sub-second startup (vs ~10s on JVM)
- ~40-60 MB memory (vs ~150-220 MB on JVM)
- ~50-80 MB Docker image (vs ~300 MB with JRE)

## API Endpoints

- `GET /api/haiku` — Haiku-style sentence (5-7-5 syllables)
- `GET /api/couplet` — Two rhyming lines
- `GET /api/comparison` — Comparison sentence ("X e mai adj decât Y")
- `GET /api/definition` — Dictionary-style definition
- `GET /api/tautogram` — All words start with same letter
- `GET /api/mirror` — ABBA rhyme scheme (4 lines)
- `GET /q/health` — Health check

## Local Development

### Prerequisites

- Java 21 / GraalVM 21 (e.g., `brew install --cask graalvm-jdk@21`)
- Gradle 8.11 (wrapper included)
- Docker (for running integration tests via Testcontainers)
- Supabase project with the `words` table populated

### First-time setup

```bash
# Enable credential-scanning pre-commit hook
git config core.hooksPath .githooks
```

### Environment variables

```bash
export SUPABASE_DB_URL=jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres
export SUPABASE_DB_USER=postgres
export SUPABASE_DB_PASSWORD=<your-password>
```

### Loading the dictionary

```bash
# Download and load dictionary into Supabase (one-time setup)
./gradlew loadDictionary
```

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

### Building

```bash
# JVM build
./gradlew build

# Native build (requires GraalVM)
./gradlew build -Dquarkus.native.enabled=true
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
│   └── tools/                       # LoadDictionary data loader
├── frontend/                        # Static frontend for GitHub Pages
├── Dockerfile                       # GraalVM native multi-stage build
├── render.yaml                      # Render deployment config
└── .github/workflows/               # CI/CD pipelines
```

## Database Schema

Schema is managed by [Flyway](https://flywaydb.org/) migrations in `src/main/resources/db/migration/`. Flyway runs automatically at startup (`quarkus.flyway.migrate-at-start=true`) and baselines existing databases.

Indexes on `(type)`, `(type, rhyme)`, `(type, syllables)`, `(type, first_letter)`, and `(type, rhyme, syllables)`.

## Acknowledgements

The Romanian word dictionary used in this project comes from [dexonline.ro](https://dexonline.ro), an exceptional open-source Romanian dictionary project. Many thanks to the dexonline team for maintaining such a comprehensive and freely available linguistic resource. Word definitions shown on hover link directly to dexonline.ro.

## License

GPL-3.0
