# Backend Codemap

Freshness: 2026-02-08

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

All sentence endpoints accept optional `rarity=1..5` (default `2`).
If constraints are impossible at low rarity, endpoints return a placeholder sentence with HTTP 200.

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

## Rarity Tooling Map

Entry point:
- `src/main/kotlin/scrabble/phrases/tools/RarityPipeline.kt`

Modular implementation:
- `src/main/kotlin/scrabble/phrases/tools/rarity/RarityCli.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/RarityStep1Exporter.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/RarityStep2Scorer.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/RarityStep3Comparator.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/RarityStep4Uploader.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/LmClient.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/LmStudioClient.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/LmModelConfig.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/LmModelConfigRegistry.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/LmModelDefaultsGptOss20b.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/LmModelDefaultsGlm47Flash.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/LmModelDefaultsMinistral38b.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/LmModelDefaultsFallback.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/LmStudioRequestSupport.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/LmStudioResponseParser.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/LmStudioHttpGateway.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/RunCsvRepository.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/UploadMarkerWriter.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/RunLockManager.kt`

Step 2 resilience utilities:
- `src/main/kotlin/scrabble/phrases/tools/rarity/JsonRepair.kt` -- best-effort repair of truncated/malformed LM JSON (trailing decimals, unclosed structures, trailing commas, line comments)
- `src/main/kotlin/scrabble/phrases/tools/rarity/BatchSizeAdapter.kt` -- adaptive batch sizing via sliding window; uses per-batch success ratio, shrinks on weak outcomes, grows on sustained success
- `src/main/kotlin/scrabble/phrases/tools/rarity/FuzzyWordMatcher.kt` -- Romanian diacritics normalization + Levenshtein distance for matching LM-misspelled words
- `src/main/kotlin/scrabble/phrases/tools/rarity/Step2Metrics.kt` -- observability: error categorization, WPM, ETA, progress formatting, end-of-run summary

Step behavior:
- Step 1/4 touch DB.
- Step 2/3 are local CSV-only.
- Step 4 default mode is `partial`; legacy global fallback writes require `--mode full-fallback`.

Step 2 safety:
- exclusive file lock on output CSV
- guarded atomic rewrite (merge latest disk + memory, then shrink checks)
- strict CSV parse (malformed rows fail; no silent skip)

Step 2 LM response handling:
- `JsonRepair` applied before JSON parsing to fix truncated output from token exhaustion
- parser matches by `word_id` first, then `word/type`, then fuzzy fallback
- lenient result extraction: partial results accepted; unresolved rows are retried in-process before split fallback
- envelope tolerant parsing: accepts `results`, `items`, `data`, or top-level arrays
- fuzzy word matching accepts diacritical misspellings
- confidence parsed as string or number; accepts both `0..1` and `1..100` (normalized to `0..1`)
- run-scoped capability cache: after one unsupported `response_format` error, remaining requests skip `response_format`
- run-scoped reasoning-controls cache (GLM): if `reasoning_effort`/`chat_template_kwargs` are unsupported, subsequent requests skip them
- model parameter defaults are centralized per model (`temperature`, `top_k`, `top_p`, `min_p`, penalties, reasoning settings)
- model crash backoff: linear delay on "model has crashed" errors
- dynamic `max_tokens`: estimated per batch and capped by `--max-tokens` (plus model-specific cap when defined)

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
- Rarity tooling unit/regression tests: `src/test/kotlin/scrabble/phrases/tools/rarity/`

## High-Risk Areas

- Breaking `" / "` delimiter usage in verse providers.
- Relaxing or removing exclusion sets and introducing duplicate words.
- Changing anchor output without frontend sanitizer updates.
- Schema changes without synchronized loader/repository/test-seed updates.
