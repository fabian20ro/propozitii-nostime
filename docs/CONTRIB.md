# Contributing Guide

> Source of truth: `build.gradle`, `application.properties`, `render.yaml`

## Prerequisites

- Java 21 / GraalVM 21 (`brew install --cask graalvm-jdk@21`)
- Docker (for integration tests via Testcontainers)
- Gradle 8.11 (wrapper included)

## First-Time Setup

```bash
git clone https://github.com/fabian20ro/propozitii-nostime.git
cd propozitii-nostime

# Enable credential-scanning pre-commit hook
git config core.hooksPath .githooks
```

No environment variables needed for local development. Quarkus Dev Services auto-provisions a PostgreSQL container.

## Available Gradle Tasks

| Task | Purpose |
|------|---------|
| `./gradlew quarkusDev` | Start dev server with live reload (auto-provisions PostgreSQL) |
| `./gradlew test` | Run all tests (unit + integration, requires Docker) |
| `./gradlew build` | Compile + package JVM build |
| `./gradlew build -Dquarkus.native.enabled=true` | Build GraalVM native image |
| `./gradlew jacocoTestReport` | Generate HTML coverage report at `build/reports/jacoco/` |
| `./gradlew downloadDictionary` | Download dexonline.ro word list (SHA-256 verified) |
| `./gradlew loadDictionary` | Parse word list and bulk-insert into PostgreSQL |

## Environment Variables

Only required in production (`%prod` profile). Dev/test mode uses Testcontainers automatically.

| Variable | Purpose | Default |
|----------|---------|---------|
| `PORT` | HTTP listen port | `8080` |
| `SUPABASE_DB_URL` | JDBC PostgreSQL URL | Required |
| `SUPABASE_DB_USER` | Database username | `postgres` |
| `SUPABASE_DB_PASSWORD` | Database password | Required |

## Development Workflow

1. Start dev server: `./gradlew quarkusDev`
2. API available at `http://localhost:8080/api/{all,haiku,couplet,comparison,definition,tautogram,mirror}`
3. Dev Services starts a PostgreSQL container with Flyway migrations + test seed data
4. Make changes -- Quarkus live-reloads automatically
5. Run tests: `./gradlew test`

## Testing

**Unit tests** (no Docker needed at test level):
- `words/` package: syllable counting, rhyme, articulation, feminine forms
- `decorators/`: capitalize, link generation, verse breaking

**Integration tests** (require Docker):
- `PhraseResourceTest`: all 6 API endpoints against a real PostgreSQL
- Testcontainers provisions `postgres:16-alpine`
- Flyway runs `V1__create_words_table.sql` + `V2__add_articulated_syllables.sql` + `V100__seed_test_data.sql` (23 test words)

### Troubleshooting Tests

**Colima (macOS)**: If Docker socket isn't at `/var/run/docker.sock`, set in `~/.testcontainers.properties`:
```properties
docker.host=unix:///Users/<you>/.colima/default/docker.sock
```
And run with: `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./gradlew test`

**Wrong Java version**: Ensure Java 21 is active. Check with `java -version`. On macOS with multiple versions:
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
```

## Credential Safety

- Pre-commit hook (`.githooks/pre-commit`) runs `gitleaks protect --staged` to scan for secrets
- CI runs `gitleaks/gitleaks-action@v2` before build
- Never commit real credentials -- use environment variables

## Loading the Dictionary (one-time)

Only needed when setting up a new Supabase instance:

```bash
export SUPABASE_DB_URL=jdbc:postgresql://db.<ref>.supabase.co:5432/postgres
export SUPABASE_DB_USER=postgres
export SUPABASE_DB_PASSWORD=<password>
./gradlew loadDictionary
```

This downloads the Romanian Scrabble dictionary (~80K words from dexonline.ro) and inserts them into the `words` table.
