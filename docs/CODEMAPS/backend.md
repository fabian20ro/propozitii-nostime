# Backend Codemap

Freshness: 2026-02-06

## Entry Points

- Resource: `src/main/kotlin/scrabble/phrases/PhraseResource.kt`
- Filters/mappers:
- `src/main/kotlin/scrabble/phrases/RateLimitFilter.kt`
- `src/main/kotlin/scrabble/phrases/GlobalExceptionMapper.kt`

## Endpoint Matrix

| Endpoint | Provider | Decorator chain | Notes |
|---|---|---|---|
| `/api/haiku` | `HaikuProvider` | `VerseLineCapitalizer -> DexonlineLinkAdder -> HtmlVerseBreaker` | 5-7-5 style via syllable constraints |
| `/api/couplet` | `CoupletProvider` | `VerseLineCapitalizer -> DexonlineLinkAdder -> HtmlVerseBreaker` | 4 lines, ABBA rhyme by noun endings |
| `/api/comparison` | `ComparisonProvider` | `FirstSentenceLetterCapitalizer -> DexonlineLinkAdder` | `X e mai adj decât Y` |
| `/api/definition` | `DefinitionProvider` | `DexonlineLinkAdder` | starts with uppercased defined noun |
| `/api/tautogram` | `TautogramProvider` | `FirstSentenceLetterCapitalizer -> DexonlineLinkAdder` | shared 2-letter prefix across N/A/V |
| `/api/mirror` | `MirrorProvider` | `VerseLineCapitalizer -> DexonlineLinkAdder -> HtmlVerseBreaker` | 4 lines, ABBA rhyme by verb endings |
| `/api/all` | Aggregates above | N/A | frontend's main fetch path |

## Provider Responsibilities

- `HaikuProvider`
- noun articulated syllables target 5 (`getRandomNounByArticulatedSyllables(5)` fallback to any noun)
- adjective syllables depend on noun gender (F:3, M/N:4)
- verb syllables fixed at 3

- `CoupletProvider`
- picks two noun rhyme groups with at least 2 words each
- reserves line-ending nouns first, then fills line starts
- uses exclusion sets to enforce uniqueness inside one sentence

- `ComparisonProvider`
- simple comparative sentence, unique second noun

- `DefinitionProvider`
- dictionary-style sentence using one defined noun and three additional words

- `TautogramProvider`
- uses cached prefix list with all three word types
- enforces second noun different from first

- `MirrorProvider`
- picks two verb rhyme groups with at least 2 words each
- builds ABBA endings using verbs

## Repository Map

File: `src/main/kotlin/scrabble/phrases/repository/WordRepository.kt`

### Cache Initialization (`@PostConstruct`)

- counts by type: for offset random selection
- counts by `(type, syllables)`
- counts by `(type, articulated_syllables)`
- noun rhyme groups (>=2 and >=3 cached separately)
- verb rhyme groups (>=2)
- valid 2-char prefixes with all 3 types

### Query Strategy

- No exclusions: uses cached counts + `LIMIT 1 OFFSET random` for several methods.
- With exclusions: uses `NOT IN (...) ORDER BY RANDOM() LIMIT 1`.
- Mapping methods build `Noun`, `Adjective`, `Verb` domain objects directly.

## Domain Word Model

Folder: `src/main/kotlin/scrabble/phrases/words/`

- `Word` sealed interface: `word`, `syllables`, `rhyme`
- `Noun`: gender + articulated form generation
- `Adjective`: feminine transformation rules + `forGender`
- `Verb`: basic word metrics
- `WordUtils`: syllables, rhyme, capitalization, text fixes

## Common Backend Change Recipes

### Add endpoint

1. Add provider class implementing `ISentenceProvider`.
2. Add `@GET` method in `PhraseResource`.
3. Apply correct decorator chain.
4. If needed, add field to `AllSentencesResponse` and `/api/all` builder.
5. Add/extend integration tests.

### Add new repository query dimension

1. Add migration column/index.
2. Extend cache initialization in `initCounts()` if it needs fast offset selection.
3. Add query method + mapper usage.
4. Update test seed data and provider logic.

## Backend Test Coverage

- Integration: `src/test/kotlin/scrabble/phrases/PhraseResourceTest.kt`
- Decorator unit tests: `src/test/kotlin/scrabble/phrases/decorators/DecoratorTest.kt`
- Morphology/utils tests: `src/test/kotlin/scrabble/phrases/words/`

## High-Risk Areas

- Breaking `" / "` delimiter usage in verse providers.
- Relaxing or removing exclusion sets and introducing duplicate words.
- Changing anchor output without frontend sanitizer updates.
- Schema changes without synchronized loader/repository/test-seed updates.
