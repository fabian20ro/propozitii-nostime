# Lessons Learned

> Maintained by AI agents. Contains validated, reusable insights.
> **Read at the start of every task. Update at the end of every iteration.**

## How to Use This File

### Reading (Start of Every Task)
Read this before writing any code to avoid repeating known mistakes.

### Writing (End of Every Iteration)
If a new reusable insight was gained, add it to the appropriate category.

### Promotion from Iteration Log
Patterns appearing 2+ times in `ITERATION_LOG.md` should be promoted here.

### Pruning
Obsolete lessons move to Archive section at bottom (with date and reason). Never delete.

---

## Architecture & Design Decisions

<!-- Format: **[YYYY-MM-DD]** Brief title — Explanation -->

**[2026-02-07]** Rarity cutoff risks constraint breaks — Restricting runtime generation to only the top ~5000 words risks breaking rhyme/prefix constraints in `Mirror` and `Tautogram`.

**[2026-02-12]** minRarity required repository-wide SQL changes — Adding `minRarity` required repository-wide SQL updates from `<=` to range filtering (`BETWEEN min AND max`) and careful cache handling.

**[2026-02-18]** Supabase SDK has no query retries — Supabase JS SDK v2 has **no built-in PostgREST query retries** — slow Vercel responses were caused by excessive sequential HTTP round-trips (up to 69 for `genMirror`), not retry backoff. Mitigations: (1) disable auth/realtime in `createClient` options for serverless, (2) cache counts per request via `CountCache`, (3) replace iterative rhyme-group probing with bulk fetch + client-side grouping, (4) parallelize independent queries within generators via `Promise.all`, (5) add per-generator timeouts via `Promise.race` to prevent one slow generator from exhausting the 10s `maxDuration`.

**[2026-02-24]** `/api/all` is the primary frontend fetch path — Forgetting this when refactoring endpoints breaks the frontend silently.

**[2026-02-24]** Dual-backend parity — Adding/changing a sentence type in Kotlin without mirroring in `api/all.ts` (or vice versa) causes response shape mismatches.

## Code Patterns & Pitfalls

<!-- Format: **[YYYY-MM-DD]** Brief title — Explanation -->

**[2026-02-11]** Kotlin `require()` lazy evaluation — Kotlin `require()` lazily evaluates its message lambda; avoid side effects in that lambda.

**[2026-02-11]** Broad exception catches hide failures — Broad exception catches in file/IO critical paths hide real failures; catch only expected exception types.

**[2026-02-12]** Kotlin default params for API expansion — Kotlin default parameters (`minRarity: Int = 1`) are useful for backward-compatible API expansion.

**[2026-02-12]** LocalStorage migration must be idempotent — LocalStorage key migrations must be idempotent and must not overwrite already-migrated values.

**[2026-02-15]** Vercel ESM mode requires explicit config — Vercel can execute transpiled `api/all.js` as CommonJS unless package mode is explicit; keep `"type": "module"` in `package.json` for ESM imports.

**[2026-02-20]** TS interfaces must match DB nullability — TypeScript interfaces in `api/all.ts` must match DB nullability. A `SMALLINT` column added without `NOT NULL` means the TS type must be `number | null`, not `number`.

**[2026-02-21]** Kotlin rarity range min/max ordering — `rarityRange` in Kotlin backend did not reorder min/max, so `?rarity=1&minRarity=5` produced an empty range. TypeScript backend already had `Math.min`/`Math.max` normalization. Fix: apply `minOf`/`maxOf` in Kotlin to match.

**[2026-02-24]** Unsanitized HTML from backend — Returning raw HTML changes from backend without updating frontend sanitizer breaks the safety contract.

**[2026-02-24]** Verse delimiter breakage — Breaking the `" / "` delimiter contract causes line breaks to silently disappear in the frontend.

## Romanian Morphology

<!-- Format: **[YYYY-MM-DD]** Brief title — Explanation -->

**[2026-02-19]** `-or` feminine has three subgroups — Adjective `-or` feminine forms have three distinct subgroups requiring specific rules: `-tor`→`-toare` (agentive), `-ior`→`-ioară` (Latin comparatives), `-șor`→`-șoară` (diminutives); neologisms (sonor, major) correctly use default `+ă`. A blanket `-or`→`-oare` rule is too broad.

**[2026-02-19]** Diminutive `-el` uses `-ică` — Diminutive adjectives ending in `-el` (via sub-suffixes `-țel`, `-șel`, `-rel`) form feminine with `-ică`, not default `+ă`. Non-diminutive `-el` words (fidel, paralel) correctly use default.

**[2026-02-19]** "negru" requires stem vowel alternation — Pre-existing edge case: "negru"→"neagră" requires a stem vowel alternation (e→ea) that simple suffix rules cannot handle; resolved via explicit `word == "negru"` special case. The alternation is lexical—"integru"→"integră" does NOT alternate—so pattern rules are unsafe.

**[2026-02-19]** Feminine forms change syllable count — Feminine adjective forms can change syllable count (11 of 17 rules tested add +1), but `-ior`, `-iu`, `-ru`, invariants, and "negru" do NOT change. Providers using syllable-based selection (HaikuProvider) must query by the gender-specific form's syllable count via `feminine_syllables` column, paralleling the `articulated_syllables` pattern for nouns.

**[2026-02-21]** Word-initial diphthong miscount — Word-initial 2-char diphthongs (ia, oa, ie, ea, ua) were skipped by the `if (i > 0)` guard in `replaceTongsWithChar`, inflating syllable counts by 1 for words like "iarbă" (returned 3 instead of 2). Triphthongs at position 0 were unaffected. Fix: collapse `i == 0` into the same branch as "after consonant".

**[2026-02-21]** Vowel alternations are lexically conditioned — Romanian vowel alternations (`e→ea`, `o→oa`) in adjective feminization are **lexically conditioned** — each word must be special-cased. Added: "drept"→"dreaptă", "întreg"→"întreagă", "deșert"→"deșeartă", "mort"→"moartă". Counter-example: "integru"→"integră" does NOT alternate.

## Data & Database

<!-- Format: **[YYYY-MM-DD]** Brief title — Explanation -->

**[2026-02-12]** `LC_ALL=C` for deterministic sorts — For deterministic shell joins/diffs by `word_id`, use `LC_ALL=C` when sorting.

**[2026-02-20]** Migration V2 lacks `IF NOT EXISTS` — Migration V2 (`ALTER TABLE ADD COLUMN`, `CREATE INDEX`) lacks `IF NOT EXISTS` — re-running it on prod crashes. Always use `IF NOT EXISTS` for DDL in new migrations since there is no Flyway tracking on prod.

**[2026-02-20]** New columns need backfill — Adding a column that the code queries in `WHERE` clauses or at startup (`initCounts`) is not enough — existing rows will have NULL and those queries return nothing. Either backfill in the migration SQL or run `loadDictionary` after migrating. HaikuProvider now has a fallback for this gap.

**[2026-02-20]** `loadDictionary` TRUNCATE destroys rarity levels — `loadDictionary` TRUNCATE destroys externally-managed `rarity_level` values; the loader must never write `rarity_level` since it is managed by the external word-rarity-classifier project. The loader now backs up and restores rarity levels across reloads.

**[2026-02-24]** Migration files are immutable — Updating migration files in-place instead of adding a new versioned migration causes Flyway validation failures.

## Testing & Quality

<!-- Format: **[YYYY-MM-DD]** Brief title — Explanation -->

**[2026-02-07]** Testcontainers Ryuk failure with Colima — `./gradlew test` can fail locally when Testcontainers cannot start Ryuk with Colima Docker socket mount (`operation not supported`).

**[2026-02-07]** Test reliability depends on Docker health — Full integration-test reliability depends on local Docker/Testcontainers health; use targeted subsets when container runtime is unstable.

**[2026-02-24]** Test seed data constraints — Introducing new provider constraints that test seed data cannot satisfy causes test failures that look like code bugs.

## Dependencies & External Services

<!-- Format: **[YYYY-MM-DD]** Brief title — Explanation -->

**[2026-02-12]** Dual-range slider pointer-events — Dual-range slider UX requires careful pointer-events handling for overlapping range inputs.

**[2026-02-14]** Node v25 breaks npm ci — Local Node `v25` + npm `11` may break `npm ci` with `Exit handler never called!`; use Node `20` for stable JS fallback tests.

**[2026-02-15]** Supabase lazy init required — Initializing Supabase at module import can hard-crash Vercel (`FUNCTION_INVOCATION_FAILED`) when `SUPABASE_URL` is malformed (e.g., JDBC URL); validate and lazily init inside request path.

**[2026-02-15]** Generator misses are expected — `No valid prefix` / similar generator misses are normal at some rarity ranges; treat them as expected unsatisfiable outcomes, not error-level logs.

## Process & Workflow

<!-- Format: **[YYYY-MM-DD]** Brief title — Explanation -->

---

## Archive

<!-- Format: **[YYYY-MM-DD] Archived [YYYY-MM-DD]** Title — Reason for archival -->

**[2026-02-07] Archived [2026-02-24]** AGENTS.md should stay an operational index — Superseded by the new SETUP_AI_AGENT_CONFIG.md guide and restructured file system. AGENTS.md is now minimal non-discoverable constraints only; operational content lives in docs/.
