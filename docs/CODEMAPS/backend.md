# Backend Codemap

Freshness: 2026-02-12

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

- All queries filter by `rarity_level BETWEEN ? AND ?` (was `<= ?` before `minRarity` support).
- No exclusions: uses cached counts + `LIMIT 1 OFFSET random` for several methods.
- With exclusions: uses `NOT IN (...) ORDER BY RANDOM() LIMIT 1`.
- Range counts derived from cumulative caches: `count(min..max) = cumulative(max) - cumulative(min-1)` via `rangeCount()` / `rangeCountTriple()` helpers.
- Rhyme/prefix caches (keyed by maxRarity only) bypass to direct DB queries when `minRarity > 1`.
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
- `src/main/kotlin/scrabble/phrases/tools/rarity/RarityStep5Rebalancer.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/LmClient.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/lmstudio/LmStudioClient.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/lmstudio/LmModelConfig.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/lmstudio/LmModelConfigRegistry.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/lmstudio/LmModelDefaultsGptOss20b.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/lmstudio/LmModelDefaultsGlm47Flash.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/lmstudio/LmModelDefaultsMinistral38b.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/lmstudio/LmModelDefaultsEuroLlm22b.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/lmstudio/LmModelDefaultsFallback.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/lmstudio/LmStudioRequestSupport.kt` (includes `LmStudioErrorClassifier` object for error classification heuristics)
- `src/main/kotlin/scrabble/phrases/tools/rarity/lmstudio/LmStudioResponseParser.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/lmstudio/LmStudioHttpGateway.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/RunCsvRepository.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/UploadMarkerWriter.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/RunLockManager.kt` (side-effect-free `acquire`: no resource cleanup hidden in `require()` lambdas)

Step 2 resilience utilities:
- `src/main/kotlin/scrabble/phrases/tools/rarity/JsonRepair.kt` -- best-effort repair of truncated/malformed LM JSON (trailing decimals, unclosed structures, trailing commas, line comments); trailing-comma removal is JSON-string-aware via `walkJsonChars` to avoid corrupting commas inside quoted values
- `src/main/kotlin/scrabble/phrases/tools/rarity/JsonStringWalker.kt` -- shared inline `walkJsonChars` for tracking in-string/escaped state across JSON characters; used by `JsonRepair` and `LmStudioResponseParser`
- `src/main/kotlin/scrabble/phrases/tools/rarity/BatchSizeAdapter.kt` -- adaptive batch sizing via sliding window; uses per-batch success ratio, shrinks on weak outcomes, grows on sustained success
- `src/main/kotlin/scrabble/phrases/tools/rarity/FuzzyWordMatcher.kt` -- Romanian diacritics normalization + Levenshtein distance for matching LM-misspelled words
- `src/main/kotlin/scrabble/phrases/tools/rarity/Step2Metrics.kt` -- observability: error categorization, WPM, ETA, progress formatting, end-of-run summary; single-threaded design (atomic counters for individual fields, used in Step 2 scoring loop)
- `src/main/kotlin/scrabble/phrases/tools/rarity/CsvCodec.kt` -- CSV read/write with strict validation; `writeTableAtomic` uses temp-file + rename with narrowed exception handling (`AtomicMoveNotSupportedException`, `UnsupportedOperationException` only)
- `src/main/kotlin/scrabble/phrases/tools/rarity/RaritySupport.kt` -- shared utilities: CLI arg parsing, run slug sanitization, `median()` (half-up via `Math.round()`), prompt file loading

Step behavior:
- Step 1/4 touch DB.
- Step 2/3/5 are local CSV-only.
- Step 4 default mode is `partial`; legacy global fallback writes require `--mode full-fallback`.

Step 2 safety:
- exclusive file lock on output CSV (side-effect-free acquire; no hidden cleanup in error paths)
- guarded atomic rewrite (merge latest disk + memory, then shrink/minId/maxId checks)
- strict CSV parse (malformed rows fail; no silent skip)
- atomic CSV writes use narrowed exception catch (`AtomicMoveNotSupportedException` + `UnsupportedOperationException`)
- recursion depth guard (max 10) on batch split/retry to prevent unbounded recursion

Step 2 interface design:
- `LmClient` interface uses `ScoringContext` parameter object (groups run slug, model, endpoint, retry/timeout settings, prompts, etc.)
- `CapabilityState` data class tracks run-scoped response-format and reasoning-control degradation

Step 2 LM response handling:
- `JsonRepair` applied before JSON parsing to fix truncated output from token exhaustion
- parser matches by `word_id` first, then `word/type`, then fuzzy fallback
- lenient result extraction: partial results accepted; unresolved rows are retried in-process before split fallback
- malformed-item tolerance: when a single returned item is malformed JSON, parser salvages valid sibling items and retries only unresolved rows
- envelope tolerant parsing: accepts `results`, `items`, `data`, or top-level arrays
- simple JSON contract: prompts request top-level array output (still envelope-tolerant at parse time)
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
  - LM response parser: `LmStudioResponseParserTest.kt` (11 tests: code fences, fuzzy diacritics, salvage, confidence normalization, etc.)
  - Step 3 comparator: `Step3ComparatorTest.kt` (9 tests: agreement, outlier detection, missing runs, 3-run merge rules, null run-c compatibility)
  - Upload markers: `UploadMarkerWriterTest.kt` (3 tests: marking, empty status, partial marking)
  - Step 2 scorer counters: `Step2ScorerCountersTest.kt` (partial results, full scoring)
  - Step 2 scorer guards: `Step2ScorerGuardTest.kt` (3 tests: shrink detection, minId increase abort, maxId decrease abort)
  - LmStudioClient integration: `LmStudioClientTest.kt` (20 tests: capability degradation, json_schema fallback, partial parse retry, selection mode splits, model profile defaults, connectivity failures, salvage malformed items)
  - LmStudioErrorClassifier: `LmStudioErrorClassifierTest.kt` (15 tests: response_format detection, json_schema switch, reasoning controls, empty results, excerpt truncation/collapse)
  - JSON repair: `JsonRepairTest.kt` (19 tests: trailing decimals, line comments, trailing commas including JSON-string-awareness, unclosed structures, full pipeline, escaped quotes)
  - CSV codec: `CsvCodecTest.kt` (5 tests: quotes/commas/UTF-8 roundtrip, malformed row detection, atomic write, empty file, column count mismatch)
  - Rarity support: `RaritySupportTest.kt` (4 tests: median odd/even length, half-up rounding, unsorted input)
  - Run lock manager: `RunLockManagerTest.kt`
  - Fuzzy word matcher: `FuzzyWordMatcherTest.kt`
  - Batch size adapter: `BatchSizeAdapterTest.kt`
  - Step 2 metrics: `Step2MetricsTest.kt`
  - Step 5 rebalancer: `Step5RebalancerTest.kt`
  - Rarity distribution: `RarityDistributionTest.kt`, `Step2DistributionFormatterTest.kt`
  - Step 2 scorer resume: `Step2ScorerResumeTest.kt`
  - Step 4 uploader: `Step4UploaderTest.kt`
  - Run CSV repository: `RunCsvRepositoryTest.kt`
  - Test doubles: `TestDoubles.kt` (`FakeLmClient`, `HalfBatchLmClient`)

## High-Risk Areas

- Breaking `" / "` delimiter usage in verse providers.
- Relaxing or removing exclusion sets and introducing duplicate words.
- Changing anchor output without frontend sanitizer updates.
- Schema changes without synchronized loader/repository/test-seed updates.
