# Data Codemap

> Freshness: 2026-02-01

## Database Schema (Flyway V1 + V2)

```sql
words (
  id                    SERIAL PRIMARY KEY,
  word                  VARCHAR(50) NOT NULL,      -- base word form
  type                  CHAR(1) NOT NULL,          -- N=noun, A=adjective, V=verb
  gender                CHAR(1),                   -- M/F/N (nouns only)
  syllables             SMALLINT NOT NULL,         -- pre-computed
  rhyme                 VARCHAR(10) NOT NULL,      -- last 3 chars of word
  first_letter          CHAR(1) NOT NULL,          -- lowercase
  articulated           VARCHAR(60),               -- definite article form (nouns only)
  feminine              VARCHAR(60),               -- feminine form (adjectives only)
  articulated_syllables SMALLINT                   -- syllable count of articulated form (V2)
)
```

Indexes: `(type, syllables)`, `(type, first_letter)`, `(type, rhyme, syllables)`, `(type, articulated_syllables)` (V2)

## Word Type Hierarchy (Kotlin)

```
sealed interface Word { word, syllables, rhyme }
  +-- Noun     { gender: NounGender, articulated: String }
  +-- Adjective { feminine: String }
  +-- Verb      (no extra fields)

enum NounGender { M, F, N }
```

## Computed Fields

| Field | Logic |
|-------|-------|
| syllables | Count vowels after collapsing Romanian diphthongs/triphthongs |
| rhyme | `word.substring(max(0, word.length - 3))` |
| articulated (M/N) | Ends with `u` -> `+l`, else `+ul` |
| articulated (F) | Ends with `ă`/`ie` -> replace last char with `a`; ends with `a` -> `+ua`; else `+a` |
| feminine (adj) | 9 suffix rules: `-esc`->`-ască`, `-eț`->`-ață`, `-or`->`-are`, `-os`->`-asă`, `-iu/-ci`->`-e`, `-ru`->`-ă`, `-e/-o/-i`->unchanged, else `+ă` |

## API Response

```json
{ "sentence": "<html with <a href='https://dexonline.ro/definitie/...'>word</a> links>" }
```

## Test Seed Data (V100)

23 words: 11 nouns, 5 adjectives, 7 verbs. Covers:
- 4 feminine nouns sharing rhyme `asă` (CoupletProvider ABBA)
- 3 masculine nouns sharing rhyme `ine` (CoupletProvider ABBA)
- 2 verb rhyme groups: `ază` (2 verbs) and `ndă` (2 verbs) for MirrorProvider ABBA
- Noun with 5-syllable articulated form (HaikuProvider)
- Nouns/adj/verbs starting with `m` (TautogramProvider)
- All providers use word exclusion to prevent duplicate words within a sentence

## Dictionary Source

~80K words from the Romanian Scrabble dictionary (`loc-baza-5.0.zip` from dexonline.ro), SHA-256 verified at download. Parsed and bulk-inserted by `LoadDictionary.kt`.
