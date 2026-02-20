# LESSONS_LEARNED

## Update Rule
- Append one short bullet whenever a blocker appears or behavior is different than expected.

## Entries
- 2026-02-07: `./gradlew test` can fail locally when Testcontainers cannot start Ryuk with Colima Docker socket mount (`operation not supported`).
- 2026-02-07: Full integration-test reliability depends on local Docker/Testcontainers health; use targeted subsets when container runtime is unstable.
- 2026-02-07: Restricting runtime generation to only the top ~5000 words risks breaking rhyme/prefix constraints in `Mirror` and `Tautogram`.
- 2026-02-07: `AGENTS.md` should stay an operational index, not a dumping ground for deep implementation notes.
- 2026-02-11: Kotlin `require()` lazily evaluates its message lambda; avoid side effects in that lambda.
- 2026-02-11: Broad exception catches in file/IO critical paths hide real failures; catch only expected exception types.
- 2026-02-12: Adding `minRarity` required repository-wide SQL updates from `<=` to range filtering (`BETWEEN min AND max`) and careful cache handling.
- 2026-02-12: Kotlin default parameters (`minRarity: Int = 1`) are useful for backward-compatible API expansion.
- 2026-02-12: Dual-range slider UX requires careful pointer-events handling for overlapping range inputs.
- 2026-02-12: LocalStorage key migrations must be idempotent and must not overwrite already-migrated values.
- 2026-02-12: For deterministic shell joins/diffs by `word_id`, use `LC_ALL=C` when sorting.
- 2026-02-14: Local Node `v25` + npm `11` may break `npm ci` with `Exit handler never called!`; use Node `20` for stable JS fallback tests.
- 2026-02-15: Initializing Supabase at module import can hard-crash Vercel (`FUNCTION_INVOCATION_FAILED`) when `SUPABASE_URL` is malformed (e.g., JDBC URL); validate and lazily init inside request path.
- 2026-02-15: Vercel can execute transpiled `api/all.js` as CommonJS unless package mode is explicit; keep `"type": "module"` in `package.json` for ESM imports.
- 2026-02-15: `No valid prefix` / similar generator misses are normal at some rarity ranges; treat them as expected unsatisfiable outcomes, not error-level logs.
- 2026-02-19: Adjective `-or` feminine forms have three distinct subgroups requiring specific rules: `-tor`→`-toare` (agentive), `-ior`→`-ioară` (Latin comparatives), `-șor`→`-șoară` (diminutives); neologisms (sonor, major) correctly use default `+ă`. A blanket `-or`→`-oare` rule is too broad.
- 2026-02-19: Diminutive adjectives ending in `-el` (via sub-suffixes `-țel`, `-șel`, `-rel`) form feminine with `-ică`, not default `+ă`. Non-diminutive `-el` words (fidel, paralel) correctly use default.
- 2026-02-19: Pre-existing edge case: "negru"→"neagră" requires a stem vowel alternation (e→ea) that simple suffix rules cannot handle; resolved via explicit `word == "negru"` special case. The alternation is lexical—"integru"→"integră" does NOT alternate—so pattern rules are unsafe.
- 2026-02-19: Feminine adjective forms can change syllable count (11 of 17 rules tested add +1), but `-ior`, `-iu`, `-ru`, invariants, and "negru" do NOT change. Providers using syllable-based selection (HaikuProvider) must query by the gender-specific form's syllable count via `feminine_syllables` column, paralleling the `articulated_syllables` pattern for nouns.
- 2026-02-20: Migration V2 (`ALTER TABLE ADD COLUMN`, `CREATE INDEX`) lacks `IF NOT EXISTS` — re-running it on prod crashes. Always use `IF NOT EXISTS` for DDL in new migrations since there is no Flyway tracking on prod.
- 2026-02-20: Adding a column that the code queries in `WHERE` clauses or at startup (`initCounts`) is not enough — existing rows will have NULL and those queries return nothing. Either backfill in the migration SQL or run `loadDictionary` after migrating. HaikuProvider now has a fallback for this gap.
- 2026-02-20: TypeScript interfaces in `api/all.ts` must match DB nullability. A `SMALLINT` column added without `NOT NULL` means the TS type must be `number | null`, not `number`.
- 2026-02-18: Supabase JS SDK v2 has **no built-in PostgREST query retries** — slow Vercel responses were caused by excessive sequential HTTP round-trips (up to 69 for `genMirror`), not retry backoff. Mitigations: (1) disable auth/realtime in `createClient` options for serverless, (2) cache counts per request via `CountCache`, (3) replace iterative rhyme-group probing with bulk fetch + client-side grouping, (4) parallelize independent queries within generators via `Promise.all`, (5) add per-generator timeouts via `Promise.race` to prevent one slow generator from exhausting the 10s `maxDuration`.
