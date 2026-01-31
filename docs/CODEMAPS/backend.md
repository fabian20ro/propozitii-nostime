# Backend Codemap

> Freshness: 2026-01-31 | 23 production files, ~850 lines

## Package Structure

```
src/main/kotlin/scrabble/phrases/
  PhraseResource.kt          # REST controller, 7 endpoints (6 individual + /api/all batch)
  SentenceResponse.kt        # JSON DTO (single sentence)
  AllSentencesResponse.kt    # JSON DTO (all 6 sentences)
  GlobalExceptionMapper.kt   # @Provider, returns generic 500 (no internal details leaked)
  RateLimitFilter.kt         # @Provider, per-IP sliding window rate limiter (30 req/min)
  words/
    Word.kt                  # sealed interface
    NounGender.kt            # enum M/F/N
    Noun.kt                  # articulation logic (masc/fem)
    Adjective.kt             # feminine derivation (9 rules)
    Verb.kt                  # minimal (word + syllables + rhyme)
    WordUtils.kt             # syllable counting, rhyme, diphthongs
  repository/
    WordRepository.kt        # @ApplicationScoped, all SQL queries
  providers/
    ISentenceProvider.kt     # fun interface
    HaikuProvider.kt         # 5-7-5 syllable structure
    CoupletProvider.kt       # ABBA embraced rhyme (4 lines, 2 rhyme groups)
    ComparisonProvider.kt    # "X e mai adj decat Y"
    DefinitionProvider.kt    # dictionary-style definition
    TautogramProvider.kt     # all words same two-letter prefix
    MirrorProvider.kt        # ABBA rhyme scheme (4 lines, verbs rhyme at line endings)
  decorators/
    FirstSentenceLetterCapitalizer.kt
    VerseLineCapitalizer.kt  # capitalizes first letter of each verse line
    DexonlineLinkAdder.kt    # wraps words in <a> to dexonline.ro
    HtmlVerseBreaker.kt      # " / " -> <br/>
  tools/
    LoadDictionary.kt        # CLI: parse word list -> PostgreSQL
```

## Decorator Chains per Endpoint

| Endpoint | Chain |
|----------|-------|
| /api/haiku | Provider -> VerseLineCapitalize -> Links -> VerseBreak |
| /api/couplet | Provider -> VerseLineCapitalize -> Links -> VerseBreak |
| /api/comparison | Provider -> Capitalize -> Links |
| /api/definition | Provider -> Links |
| /api/tautogram | Provider -> Capitalize -> Links |
| /api/mirror | Provider -> VerseLineCapitalize -> Links -> VerseBreak |

## WordRepository Query Methods (13)

Nouns (4): `getRandomNoun`, `getRandomNounByRhyme`, `getRandomNounByPrefix`, `getRandomNounByArticulatedSyllables`
Adjectives (3): `getRandomAdjective`, `getRandomAdjectiveBySyllables`, `getRandomAdjectiveByPrefix`
Verbs (4): `getRandomVerb`, `getRandomVerbByRhyme`, `getRandomVerbBySyllables`, `getRandomVerbByPrefix`
Grouping (2): `findTwoRhymeGroups`, `getRandomPrefixWithAllTypes`

**Word exclusion**: Methods that providers call multiple times accept an optional `exclude: Set<String>` parameter. When non-empty, SQL appends `AND word NOT IN (?, ...)` and falls back to `ORDER BY RANDOM()` instead of OFFSET-based random selection (since the cached counts don't account for exclusions).

**Startup caches** (`@PostConstruct`): count-by-type, count-by-(type,syllables), count-by-(type,articulated_syllables), noun rhyme groups (min 2 and min 4), verb rhyme groups (min 2), valid 2-letter prefixes with all 3 word types.

## Test Files (6, ~284 lines)

```
src/test/kotlin/scrabble/phrases/
  PhraseResourceTest.kt      # @QuarkusTest, 6 endpoints, Testcontainers
  decorators/DecoratorTest.kt # 5 unit tests (includes VerseLineCapitalizer)
  words/
    AdjectiveTest.kt          # 11 feminine form pairs
    NounTest.kt               # masc/fem/neutral articulation
    VerbTest.kt               # syllables + rhyme
    WordUtilsTest.kt          # 28 Romanian syllable counts
```

## Configuration

- `application.properties`: `%prod` DB credentials, Flyway migrate-at-start, pool max=4
- `test/application.properties`: postgres:16-alpine, Flyway with testmigration location
