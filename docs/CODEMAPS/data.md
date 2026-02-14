# Data Codemap

Freshness: 2026-02-15

## Database Schema

Primary table: `words`

Defined by:
- `src/main/resources/db/migration/V1__create_words_table.sql`
- `src/main/resources/db/migration/V2__add_articulated_syllables.sql`
- `src/main/resources/db/migration/V3__rarity_level.sql`

Columns:
- `id` (PK)
- `word` (base form)
- `type` (`N`, `A`, `V`)
- `gender` (`M`, `F`, `N`, nouns only)
- `syllables`
- `rhyme` (last up to 3 chars)
- `first_letter`
- `articulated` (nouns)
- `feminine` (adjectives)
- `articulated_syllables` (nouns)
- `rarity_level` (1..5, default 4)

Indexes (important for random/filter lookups):
- `(type, syllables)`
- `(type, first_letter)`
- `(type, rhyme, syllables)`
- `(type, articulated_syllables)`
- `(type)`
- `(type, rarity_level, syllables)`
- `(type, rarity_level, rhyme)`
- `(type, rarity_level, articulated_syllables)`
- `(type, rarity_level, first_letter)`

## Data Producers

### Dictionary loader

File: `src/main/kotlin/scrabble/phrases/tools/LoadDictionary.kt`

Pipeline:
1. Read `src/main/resources/words.txt`.
2. Parse word + source type.
3. Derive computed fields using domain rules (`WordUtils`, `Noun`, `Adjective`, `Verb`).
4. Bulk insert rows into `words`.

The Gradle task `downloadDictionary` validates dictionary ZIP SHA-256 before extracting.

### External rarity classifier

Offline rarity classification is now maintained outside this repository.
This project uses existing `words.rarity_level` values for runtime filtering only.

External repository:
- https://github.com/fabian20ro/word-rarity-classifier

Overview document in this repo:
- `docs/rarity-classification-system.md`

## Data Consumers

### Kotlin backend (`WordRepository`)

Drives all runtime queries:
- by type
- by rhyme
- by syllable count
- by articulated syllable count
- by 2-letter prefix

With exclusion sets, repository switches to `NOT IN (...) ORDER BY RANDOM()` to preserve uniqueness.

### Vercel serverless fallback (`api/all.ts`)

Queries the same `words` table via Supabase PostgREST (JS client):
- uses `randomRow` (count + random offset) for single-word selection
- uses `.or()` multi-prefix filter for batch tautogram prefix probing
- uses `GROUP BY rhyme HAVING COUNT(*) >= 2` RPC-style query for mirror rhyme groups
- no startup caches; all queries are per-request

## Provider Data Requirements

| Provider | Required data shape |
|---|---|
| `HaikuProvider` | nouns with articulated syllables 5 preferred; adjectives with 3 or 4 syllables; verbs with 3 syllables |
| `DistihProvider` | at least 4 nouns + 4 adjectives + 2 verbs for uniqueness |
| `ComparisonProvider` | at least 2 nouns + 1 adjective |
| `DefinitionProvider` | at least 3 nouns + 1 adjective + 1 verb |
| `TautogramProvider` | at least one 2-letter prefix with N + A + V words |
| `MirrorProvider` | at least 2 verb rhyme groups with at least 2 words/group; enough nouns/adjectives |

## Test Seed Dataset

Seed file:
- `src/test/resources/db/testmigration/V100__seed_test_data.sql`

Current seed volume: 24 rows
- nouns: 11
- adjectives: 5
- verbs: 8

Purpose of seeded constraints:
- deterministic availability for rhyme-based providers
- presence of tautogram-capable prefix (`ma`)
- presence of haiku-capable articulated syllable nouns

## Data Change Checklist

When changing lexical rules/schema:
1. Add migration (`V*.sql`), do not edit historical applied migrations.
2. Update loader logic (if computed fields changed).
3. Update repository query/caches if new dimensions are queried frequently.
4. Update seed data to satisfy provider constraints.
5. Run `./gradlew test`.
