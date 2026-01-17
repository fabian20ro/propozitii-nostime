# Propozitii Nostime

[![Build Backend](https://github.com/fabian20ro/propozitii-nostime/actions/workflows/backend.yml/badge.svg)](https://github.com/fabian20ro/propozitii-nostime/actions/workflows/backend.yml)
[![Deploy Frontend](https://github.com/fabian20ro/propozitii-nostime/actions/workflows/frontend.yml/badge.svg)](https://github.com/fabian20ro/propozitii-nostime/actions/workflows/frontend.yml)

Generator de propozitii hazoase in limba romana (Romanian funny sentence generator).

**Live Demo:** https://fabian20ro.github.io/propozitii-nostime/

## Architecture

| Component | Technology | Hosting |
|-----------|------------|---------|
| Backend | Java 21 + Quarkus 3.17 | [Render](https://propozitii-nostime.onrender.com/q/health) |
| Frontend | Static HTML/CSS/JS | [GitHub Pages](https://fabian20ro.github.io/propozitii-nostime/) |
| Dictionary | [dexonline.ro](https://dexonline.ro) Scrabble word list | Downloaded at build time |

## API Endpoints

- `GET /api/haiku` - Generate a haiku-style sentence (5-7-5 syllables)
- `GET /api/five-word` - Generate a five-word sentence
- `POST /api/reset` - Reset the rhyme providers
- `GET /q/health` - Health check

## Local Development

### Prerequisites

- Java 21 (e.g., `brew install --cask temurin@21`)
- Gradle 8.11 (wrapper included)

### Running locally

```bash
# Set Java 21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

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
./gradlew build
```

## Project Structure

```
propozitii-nostime/
├── src/main/java/scrabble/phrases/
│   ├── PhraseResource.java      # REST API endpoints
│   ├── DictionaryProducer.java  # CDI bean for dictionary
│   ├── words/                   # Word types (sealed interface + records)
│   ├── dictionary/              # Word dictionary with filtering
│   ├── providers/               # Sentence generators
│   └── decorators/              # Sentence decorators (links, formatting)
├── frontend/                    # Static frontend for GitHub Pages
├── Dockerfile                   # Multi-stage build for Render
├── render.yaml                  # Render deployment config
└── .github/workflows/           # CI/CD pipelines
```

## License

GPL-3.0
