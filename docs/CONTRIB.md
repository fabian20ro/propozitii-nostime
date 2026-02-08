# Contributing Guide

Source of truth: `build.gradle`, `src/main/resources/application.properties`

## Prerequisites

- Java 21
- Docker (for Testcontainers and Quarkus Dev Services PostgreSQL)
- Gradle wrapper (`./gradlew`, bundled)

Optional:
- GraalVM 21 only if you explicitly need native-image builds

## First-Time Setup

```bash
git clone https://github.com/fabian20ro/propozitii-nostime.git
cd propozitii-nostime

# Enable credential-scanning pre-commit hook
git config core.hooksPath .githooks
```

## High-Value Docs

- Agent guide: `AGENTS.md`
- Onboarding: `docs/ONBOARDING.md`
- Codemap index: `docs/CODEMAPS/README.md`
- Rarity campaign runbook: `docs/rarity-runbook.md`

## Core Commands

| Command | Purpose |
|---|---|
| `./gradlew quarkusDev` | Start dev backend with live reload |
| `./gradlew test` | Run unit + integration tests |
| `./gradlew build` | Build JVM artifact |
| `./gradlew jacocoTestReport` | Generate coverage report |
| `./gradlew downloadDictionary` | Download + checksum-verify dictionary |
| `./gradlew loadDictionary` | Load dictionary into target PostgreSQL |

Rarity-specific regression suite (includes parser, comparator, scorer, upload marker tests):
- `./gradlew test --tests 'scrabble.phrases.tools.rarity.*'`

## Environment Variables

### Local dev/test

- Usually none required.
- Quarkus Dev Services provisions PostgreSQL containers automatically.

### Production-like runs

- `SUPABASE_DB_URL`
- `SUPABASE_DB_USER` (default `postgres`)
- `SUPABASE_DB_PASSWORD`
- `PORT` (default `8080`)

## Testing

### Unit tests

- morphology and word utilities (`words/` tests)
- decorators

### Integration tests (`@QuarkusTest`)

- endpoint-level API behavior with real PostgreSQL container and Flyway migrations
- uses migrations + test seed data from `src/test/resources/db/testmigration/`

## Change Workflow

1. Run `./gradlew test` before changes.
2. Implement change.
3. Run `./gradlew test` again.
4. Update docs when behavior/contracts change.

## Data/Schema Changes

When altering dictionary semantics or repository query dimensions:
1. Add a new migration under `src/main/resources/db/migration/`.
2. Update loader (`LoadDictionary.kt`) if computed fields changed.
3. Update repository query/caches.
4. Update integration seed data (`V100__seed_test_data.sql`).
5. Re-run tests.

## Credential Safety

- Pre-commit hook runs `gitleaks` on staged changes.
- CI runs `gitleaks` as well.
- Never commit real credentials.
