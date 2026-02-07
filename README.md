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

All sentence endpoints accept optional query parameter `rarity=1..5` (default `2`):
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
# Step 1: export source words from Supabase
./gradlew rarityStep1Export

# Step 2a: score run #1 into local CSV (repeatable/resumable)
./gradlew rarityStep2Score --args="--run gpt_oss_20b_v1 --model openai/gpt-oss-20b --base-csv build/rarity/step1_words.csv --output-csv build/rarity/runs/gpt_oss_20b_v1.csv --batch-size 20 --system-prompt-file docs/rarity-prompts/system_prompt_ro.txt --user-template-file docs/rarity-prompts/user_prompt_template_ro.txt"

# Step 2b: score run #2 later (same machine, sequential run)
./gradlew rarityStep2Score --args="--run glm47_flash_v1 --model zai-org/glm-4.7-flash --base-csv build/rarity/step1_words.csv --output-csv build/rarity/runs/glm47_flash_v1.csv --batch-size 20 --system-prompt-file docs/rarity-prompts/system_prompt_ro.txt --user-template-file docs/rarity-prompts/user_prompt_template_ro.txt"

# Step 3: local comparison + outliers CSV
./gradlew rarityStep3Compare --args="--run-a-csv build/rarity/runs/gpt_oss_20b_v1.csv --run-b-csv build/rarity/runs/glm47_flash_v1.csv --output-csv build/rarity/step3_comparison.csv --outliers-csv build/rarity/step3_outliers.csv --outlier-threshold 2"

# Step 4: upload final CSV to Supabase (default mode=partial, only IDs present in final CSV)
./gradlew rarityStep4Upload --args="--final-csv build/rarity/step3_comparison.csv"

# Optional legacy mode: update all words, fallback=4 for IDs missing from final CSV
./gradlew rarityStep4Upload --args="--final-csv build/rarity/step3_comparison.csv --mode full-fallback"
```

Artifacts:
- `build/rarity/runs/<run>.jsonl` raw LMStudio request/response log
- `build/rarity/runs/<run>.csv` normalized per-word run output
- `build/rarity/runs/<run>.state.json` step2 runtime state (pid/host/start/end/status)
- `build/rarity/failed_batches/<run>.failed.jsonl` failures after retries/split
- `build/rarity/step3_comparison.csv` and `build/rarity/step3_outliers.csv`
- `build/rarity/step4_upload_report.csv`
- marker columns are written back into `--final-csv` (`uploaded_at`, `uploaded_level`, `upload_status`, `upload_batch_id`)
  - if input CSV is read-only, marker output goes to `<final-csv>.upload_markers.csv`

Resume tip:
- Rerun the same `rarityStep2Score` command with the same `--output-csv`; already scored `word_id`s are skipped.
- Step2 takes an exclusive lock on `<run>.csv.lock`; a second writer to the same output file fails fast.
- Step2 guarded rewrite aborts if a rewrite would shrink row cardinality.

Steps 2 and 3 are fully local (CSV-only). Supabase writes happen only in step 4 upload.

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
│   └── tools/                       # LoadDictionary + RarityPipeline entrypoint
│       └── rarity/                  # Modular rarity pipeline implementation
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
