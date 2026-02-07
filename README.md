# Propozitii Nostime

[![Build Backend](https://github.com/fabian20ro/propozitii-nostime/actions/workflows/backend.yml/badge.svg)](https://github.com/fabian20ro/propozitii-nostime/actions/workflows/backend.yml)
[![Deploy Frontend](https://github.com/fabian20ro/propozitii-nostime/actions/workflows/frontend.yml/badge.svg)](https://github.com/fabian20ro/propozitii-nostime/actions/workflows/frontend.yml)

Generator de propozitii hazoase in limba romana (Romanian funny sentence generator).

**Live Demo:** https://fabian20ro.github.io/propozitii-nostime/

## Architecture

| Component | Technology | Hosting |
|-----------|------------|---------|
| Backend | Kotlin + Quarkus 3.17 (JVM) | [Render.com](https://propozitii-nostime.onrender.com/q/health) |
| Frontend | Static HTML/CSS/JS | [GitHub Pages](https://fabian20ro.github.io/propozitii-nostime/) |
| Database | PostgreSQL ([Supabase](https://supabase.com)) | Supabase Free Tier |
| Dictionary | [dexonline.ro](https://dexonline.ro) Scrabble word list | Loaded into Supabase |

### Zero-cost stack

All three services run on free tiers: Render.com (backend), Supabase (database), and GitHub Pages (frontend).

The Romanian Scrabble dictionary (~80K words) is stored in Supabase PostgreSQL with indexed columns for type, rhyme, syllable count, and first letter. Each API request queries the database directly — no in-memory dictionary, no mutable state, no reset needed.

The backend runs as a JVM uber-jar on Render.com free tier. Cold starts may take up to 60 seconds; the frontend health-polls and shows a loading message until the backend is ready.

## API Endpoints

- `GET /api/all` — All 6 sentence types in a single response
- `GET /api/haiku` — Haiku-style sentence (5-7-5 syllables)
- `GET /api/couplet` — ABBA embraced rhyme (4 lines, 2 rhyme groups)
- `GET /api/comparison` — Comparison sentence ("X e mai adj decât Y")
- `GET /api/definition` — Dictionary-style definition
- `GET /api/tautogram` — All words start with same two-letter prefix
- `GET /api/mirror` — ABBA rhyme scheme (4 lines)
- `GET /q/health` — Health check

All sentence endpoints accept optional query parameter `strangeness=1..5` (default `2`):
- `1` = very common words
- `5` = very rare/archaic/regional words

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

### Rarity scoring pipeline (LMStudio, resumable)

```bash
# Step 1: export words into the work table + CSV snapshot
./gradlew rarityStep1Export

# Step 2: score a run with a local LMStudio model (repeatable/resumable)
./gradlew rarityStep2Score --args="--run model_a_q4 --model qwen2.5-7b-instruct --batch-size 40"

# Step 3: compare runs and detect outliers
./gradlew rarityStep3Compare --args="--runs model_a_q4,model_b_q4 --outlier-threshold 2"

# Step 4: upload final median rarity levels to words.rarity_level
./gradlew rarityStep4Upload --args="--runs model_a_q4,model_b_q4,tie_break"
```

If a word has no computed level yet, runtime behavior treats it as level `4`.

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

## Developer Onboarding Docs

- Agent operating guide: `AGENTS.md`
- New contributor ramp-up: `docs/ONBOARDING.md`
- Codemap index: `docs/CODEMAPS/README.md`

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
│   └── tools/                       # LoadDictionary + RarityPipeline utilities
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
