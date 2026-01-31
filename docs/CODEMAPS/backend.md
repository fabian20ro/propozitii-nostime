# Backend Codemap

> Freshness: 2026-01-31 | 21 production files, ~785 lines

## Package Structure

```
src/main/kotlin/scrabble/phrases/
  PhraseResource.kt          # REST controller, 6 endpoints
  SentenceResponse.kt        # JSON DTO
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
    CoupletProvider.kt       # two rhyming lines (AABB)
    ComparisonProvider.kt    # "X e mai adj decat Y"
    DefinitionProvider.kt    # dictionary-style definition
    TautogramProvider.kt     # all words same two-letter prefix
    MirrorProvider.kt        # ABBA rhyme scheme (4 lines)
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

## WordRepository Query Methods (14)

Nouns (5): `getRandomNoun`, `getRandomNounByRhyme`, `getRandomNounByPrefix`, `getRandomNounByArticulatedSyllables`, `getNounsByRhyme`
Adjectives (3): `getRandomAdjective`, `getRandomAdjectiveBySyllables`, `getRandomAdjectiveByPrefix`
Verbs (3): `getRandomVerb`, `getRandomVerbBySyllables`, `getRandomVerbByPrefix`
Grouping (3): `findRhymeGroup`, `findTwoRhymeGroups`, `getRandomPrefixWithAllTypes`

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

- `application.properties`: `%prod` DB credentials, Flyway migrate-at-start, pool max=2
- `test/application.properties`: postgres:16-alpine, Flyway with testmigration location
