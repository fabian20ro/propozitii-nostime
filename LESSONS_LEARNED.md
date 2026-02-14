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
