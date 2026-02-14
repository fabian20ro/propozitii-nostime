# Backend Codemap

Freshness: 2026-02-15

## Entry Points

- Resource: `src/main/kotlin/scrabble/phrases/PhraseResource.kt`
- Filters/mappers:
- `src/main/kotlin/scrabble/phrases/RateLimitFilter.kt`
- `src/main/kotlin/scrabble/phrases/GlobalExceptionMapper.kt`

## Endpoint Matrix

| Endpoint | Provider | Decorator chain | Notes |
|---|---|---|---|
| `/api/haiku` | `HaikuProvider` | `VerseLineCapitalizer -> DexonlineLinkAdder -> HtmlVerseBreaker` | 5-7-5 style via syllable constraints |
| `/api/distih` | `DistihProvider` | `VerseLineCapitalizer -> DexonlineLinkAdder -> HtmlVerseBreaker` | 2 lines, noun-adj-verb-noun-adj pattern, no rhyme |
| `/api/comparison` | `ComparisonProvider` | `FirstSentenceLetterCapitalizer -> DexonlineLinkAdder` | `X e mai adj decât Y` |
| `/api/definition` | `DefinitionProvider` | `DexonlineLinkAdder` | starts with uppercased defined noun |
| `/api/tautogram` | `TautogramProvider` | `FirstSentenceLetterCapitalizer -> DexonlineLinkAdder` | shared 2-letter prefix across N/A/V |
| `/api/mirror` | `MirrorProvider` | `VerseLineCapitalizer -> DexonlineLinkAdder -> HtmlVerseBreaker` | 4 lines, ABBA rhyme by verb endings |
| `/api/all` | Aggregates above | N/A | frontend's main fetch path |

All sentence endpoints accept optional `rarity=1..5` (default `2`) and `minRarity=1..5` (default `1`).
`PhraseResource` normalizes both via `rarityRange()` and passes `(min, max)` to provider factories via `generateVerse()` / `generateSentence()` helpers.
If constraints are impossible at the chosen rarity range, endpoints return a placeholder sentence with HTTP 200.

## Provider Responsibilities

- `HaikuProvider`
- noun articulated syllables target 5 (`getRandomNounByArticulatedSyllables(5)` fallback to any noun)
- adjective syllables depend on noun gender (F:3, M/N:4)
- verb syllables fixed at 3

- `DistihProvider`
- 2 lines with identical structure: noun adj verb noun adj
- no rhyme constraints; uses exclusion sets to enforce word uniqueness

- `ComparisonProvider`
- simple comparative sentence, unique second noun

- `DefinitionProvider`
- dictionary-style sentence using one defined noun and three additional words

- `TautogramProvider`
- uses cached prefix list with all three word types (when `minRarity <= 1`)
- non-cached path: batch-probes 5 random nouns → verifies their prefixes in one targeted GROUP BY query
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
- valid 2-char prefixes with all 3 types (keyed by maxRarity)

### Query Strategy

- All queries filter by `rarity_level BETWEEN ? AND ?` (was `<= ?` before `minRarity` support).
- No exclusions: uses cached counts + `LIMIT 1 OFFSET random` for several methods.
- With exclusions: uses `NOT IN (...) ORDER BY RANDOM() LIMIT 1`.
- Range counts derived from cumulative caches: `count(min..max) = cumulative(max) - cumulative(min-1)` via `rangeCount()` / `rangeCountTriple()` helpers.
- Rhyme/prefix caches (keyed by maxRarity only) bypass to direct DB queries when `minRarity > 1`.
- Mapping methods build `Noun`, `Adjective`, `Verb` domain objects directly.

## External Rarity Classifier

Offline rarity classification tooling was moved out of this repository.
Current backend scope is runtime sentence generation and filtering by existing `rarity_level`.

External repository:
- https://github.com/fabian20ro/word-rarity-classifier

Overview in this repo:
- `docs/rarity-classification-system.md`

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

## Vercel Serverless Fallback

File: `api/all.ts`
Config: `vercel.json` (512MB, 10s max, single `/api/all` rewrite)

A self-contained TypeScript function that mirrors the Kotlin `/api/all` endpoint using the Supabase JS client (PostgREST). Used as a cold-start bypass when Render is sleeping.

### Structure

- Supabase client init + env validation (`SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`; service-role fallback only via explicit opt-in flag)
- Word types: `Noun`, `Adjective`, `Verb`
- Generic `randomRow<T>` helper: count query + random offset
- Six provider functions matching Kotlin providers: `comparison`, `definition`, `distih`, `haiku`, `mirror`, `tautogram`
- Inline decorators: HTML escaping, dexonline link wrapping, verse line breaking
- Accepts `?minRarity=&rarity=` query params (same contract as Kotlin)

### Tautogram Prefix Strategy (batch probe)

1. Count nouns in rarity range → pick random offset → fetch 5 nouns (2 queries)
2. Extract distinct 2-letter prefixes from sample
3. Single `.or(word.like.ab%,word.like.co%,...)` query → fetch all matching words
4. Count types per prefix client-side → return first valid prefix (all 3 types + ≥2 nouns)

Total: 3 Supabase queries instead of up to 60 sequential probes.

### Mirror Rhyme Strategy

- Picks random verbs and validates each rhyme by count query (`COUNT >= 2`)
- Up to 15 attempts to collect 2 distinct valid rhyme groups

### Parity Contract

Must return the same 6-key JSON shape and produce the same HTML decorations (dexonline `<a>` links, `<br/>` verse breaks) as the Kotlin backend.

## High-Risk Areas

- Breaking `" / "` delimiter usage in verse providers.
- Relaxing or removing exclusion sets and introducing duplicate words.
- Changing anchor output without frontend sanitizer updates.
- Schema changes without synchronized loader/repository/test-seed updates.
- Vercel function diverging from Kotlin backend (different response shape, missing decorators, or sentence type drift).
