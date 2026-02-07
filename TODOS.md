# TODOS

## Rarity Roadmap (Current State)

### Completed
- [x] Runtime filter uses `rarity_level <= rarity` in repository queries.
- [x] Public API query parameter standardized to `rarity=1..5` (default `2`).
- [x] Frontend slider standardized to `Raritate cuvinte` with persistent local storage.
- [x] CSV-first rarity pipeline implemented:
  - Step 1 DB export
  - Step 2 local LM scoring per run CSV
  - Step 3 local comparison/outlier detection
  - Step 4 DB upload from final CSV
- [x] Step 2 overwrite guards implemented:
  - exclusive lock file per run CSV
  - guarded atomic rewrite with anti-shrink checks
  - strict CSV parsing (no silent row loss)
- [x] Step 4 default mode changed to `partial`; legacy global fallback kept behind `--mode full-fallback`.

### Next Improvements
- [ ] Add optional `--dry-run` mode to `rarityStep4Upload` that writes report/markers without DB updates.
- [ ] Add CLI help output (`--help`) per rarity step with examples and defaults.
- [ ] Add deterministic integration fixture for LM response parsing edge cases (JSON fences, malformed counts, confidence range).
- [ ] Add an operational command to reconcile multiple run CSV snapshots by `word_id` + latest `scored_at`.
- [ ] Add docs section with recommended runbook for long-running Step 2 jobs (monitoring + safe resume + interruption handling).

### Nice to Have
- [ ] Add optional progress checkpoint metrics file (`processed`, `remaining`, `eta`) during Step 2.
- [ ] Add optional parallelism for Step 2 batches with bounded workers (keeping single-writer CSV lock semantics).
- [ ] Add command to export DB rarity distribution histogram before/after upload.
