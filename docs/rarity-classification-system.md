# Rarity Classification System (External)

## Status

The offline rarity-classification system is no longer implemented in this repository.
Current location:
- https://github.com/fabian20ro/word-rarity-classifier

This app (`propozitii-nostime`) only consumes `words.rarity_level` at runtime via `minRarity`/`rarity` query parameters.

## What The Classifier Does Today

The external classifier is a standalone pipeline that:

1. Reads words from Supabase PostgreSQL (`words` table).
2. Exports a deterministic source CSV (`word_id, word, type`).
3. Scores lexical rarity (`1..5`) with local LLMs using strict JSON contracts.
4. Optionally compares multiple runs and merges levels.
5. Optionally rebalances target level distributions in controlled LM batches.
6. Runs quality gates before upload:
   - L1 set stability (Jaccard vs reference run)
   - L1 anchor precision/recall (curated base-vocabulary anchors)
7. Uploads final levels back to Supabase (`words.rarity_level`) in partial mode by default.

## Implementation Shape (Language-Agnostic)

Core implementation is in Python (CLI + pipeline modules):
- CSV-first orchestration
- resumable batch runs
- strict parser/schema enforcement
- checkpointed rebalance transitions

Supporting pieces:
- prompt files (Romanian scoring + rebalance prompts)
- audit tooling for Jaccard/anchor metrics
- optional chained rebalance helper for fixed histogram campaigns

## Data Contracts

- Level semantics: lower number = more common word.
- Output levels constrained to `1..5`.
- Rebalance selection mode uses batch-local IDs only (`local_id` in `1..N`, unique, exact count, no `0`).
- Upload default is partial (only rows present in candidate CSV).

## Operational Lessons Learned

1. Histogram match alone is not quality; semantic gates (Jaccard + anchor precision/recall) are mandatory.
2. Strict parser contracts are safer than permissive auto-fill in long rebalancing runs.
3. Prompt and parser contracts must match exactly (especially ID semantics and exact-count rules).
4. Local-model JSON instability must be handled with retries, partial salvage, and bounded batch splitting.
5. Run-level artifacts (state/checkpoints/logs) are essential for multi-hour recovery and reproducibility.
6. Deterministic decoding profiles improve structured-output stability for classification tasks.
7. Pair-level rebalancing is more stable with stratified source mixing per batch.
8. Anchor sets must be curated and expanded over time; small anchor lists are only seed-level protection.

## Integration Boundary With This App

This app relies on these guarantees from the external classifier:
- `words.rarity_level` stays in `1..5`.
- Lower levels correspond to more common words.
- Updates are uploaded explicitly and auditable.

No classifier runtime code, prompts, scripts, or step orchestration are kept in this repository.
