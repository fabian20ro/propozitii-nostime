# LESSONS_LEARNED

## Update Rule
- Append one short bullet whenever a blocker appears or behavior is different than expected.

## Entries
- 2026-02-07: `./gradlew test` failed locally because Testcontainers could not start Ryuk with Colima Docker socket mount (`operation not supported`).
- 2026-02-07: Full integration test reliability depends on local Docker/Testcontainers setup; use unit-test subsets for quick validation when container runtime is broken.
- 2026-02-07: Restricting generation to only the top 5000 common words is likely to break rhyme/prefix constraints in `Couplet`, `Mirror`, and `Tautogram`.
- 2026-02-07: `AGENTS.md` should stay minimal as an activation/index file; `CLAUDE.md` is only a pointer to `AGENTS.md`.
