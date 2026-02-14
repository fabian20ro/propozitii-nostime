# Rarity Recovery Handover (2026-02-14)

This document is the operator handoff for rebuilding rarity after the unstable 2026-02-13 upload.
It is designed to be runnable without prior context.

## Executive Summary

- Last uploaded rarity levels hit target histogram but failed semantic quality for L1.
- Main failure mode was Step5 selection contract drift (invalid IDs, duplicates, out-of-range values) amplified by permissive parsing.
- Pipeline is now hardened (strict Step5 schema/parsing + stronger prompts + built-in quality gate script).
- Next upload must pass both:
  - L1 Jaccard against trusted reference,
  - anchor L1 precision threshold.

## What Failed (Measured)

Source artifacts:
- `build/rarity/runs/chain_gptoss_rerun_0213_0044_step8.csv`
- `build/rarity/step4_upload_report.csv`

Measured issues:
- Exact target distribution was achieved but was not trustworthy semantically:
  - `1:2500 2:7500 3:15000 4:22698 5:30000`
- L1 instability in same chain:
  - L1 Jaccard (`step1` vs `step8`) = `0.0929`
  - overlap = `425/2500`
- Massive level churn:
  - changed IDs (`step1` vs `step8`) = `52088/77698` (`67.04%`)
- Step5 malformed selection pressure:
  - expected selections = `75198`
  - valid unique local IDs returned = `58145`
  - missing/invalid pressure = `17053` (`22.7%`)

## What Was Changed (Implemented)

### 1) Step5 selection contract hardening

- Strict schema for selected IDs:
  - `uniqueItems: true`
  - `minimum: 1`
  - `maximum: batch_size`
- File: `src/main/kotlin/scrabble/phrases/tools/rarity/lmstudio/LmStudioRequestSupport.kt`

- Strict parser behavior:
  - no silent top-up to expected count,
  - local-id contract enforced (no word_id compatibility path),
  - invalid partial selections now fail/retry/split.
- File: `src/main/kotlin/scrabble/phrases/tools/rarity/lmstudio/LmStudioResponseParser.kt`

### 2) Prompt rewrites

- Step2 prompts rewritten to prioritize true core vocabulary semantics over per-batch quota behavior.
- Files:
  - `docs/rarity-prompts/system_prompt_ro.txt`
  - `docs/rarity-prompts/user_prompt_template_ro.txt`

- Step5 prompts now explicitly require exact count with strict local_id constraints.
- Files:
  - `docs/rarity-prompts/rebalance_system_prompt_ro.txt`
  - `docs/rarity-prompts/rebalance_user_prompt_template_ro.txt`

### 3) Quality gate tooling (Jaccard + anchor precision baked in)

- New audit tool:
  - `scripts/rarity_quality_audit.cjs`
- New seed anchor list:
  - `docs/rarity-anchor-l1-ro.txt`
- Chain script integration:
  - `scripts/rarity_rebalance_target_distribution.sh` now supports:
    - `--reference-csv`
    - `--anchor-l1-file`
    - `--min-l1-jaccard`
    - `--min-anchor-l1-precision`
    - `--min-anchor-l1-recall`

## What Worked vs Didn’t

Worked:
- Deterministic decoding profiles (`temperature=0`) for Step2/Step5.
- Step5 sparse-output strategy (selected IDs only) when contract is strict.
- Resume/checkpoint chain execution for long runs.

Didn’t work:
- Forcing fixed global histogram as primary objective.
- Permissive Step5 parser fallback that auto-filled missing selections.
- Trusting distribution-level metrics without set-level stability checks.

## Run Plan (Cold Start)

### Preconditions

1. Ensure LMStudio endpoint is healthy.
2. Ensure you have:
   - one candidate output CSV path,
   - one trusted reference CSV for L1 comparison,
   - anchor set file (`docs/rarity-anchor-l1-ro.txt`, expand over time).

### Step A: Produce candidate run

Run Step2 and optional Step5 as needed (see `docs/rarity-runbook.md` for full command matrix).

### Step B: Mandatory quality gate

Run:

```bash
node scripts/rarity_quality_audit.cjs \
  --candidate-csv build/rarity/runs/<candidate>.csv \
  --reference-csv build/rarity/runs/<reference>.csv \
  --anchor-l1-file docs/rarity-anchor-l1-ro.txt \
  --min-l1-jaccard 0.80 \
  --min-anchor-l1-precision 0.90
```

Gate policy:
- If command exits non-zero: **do not upload**.
- If command exits zero: proceed to Step C.

### Step C: Upload

```bash
./gradlew rarityStep4Upload --args="--final-csv build/rarity/runs/<candidate>.csv"
```

## Optional: Use gate inside chained rebalance command

```bash
scripts/rarity_rebalance_target_distribution.sh \
  --input-csv build/rarity/runs/<input>.csv \
  --run-base <slug> \
  --model openai/gpt-oss-20b \
  --reference-csv build/rarity/runs/<reference>.csv \
  --anchor-l1-file docs/rarity-anchor-l1-ro.txt \
  --min-l1-jaccard 0.80 \
  --min-anchor-l1-precision 0.90
```

The script now runs quality audit at the end and fails if thresholds are not met.

## Dos / Don’ts

Do:
- Gate every upload with Jaccard + anchor precision.
- Keep Step5 prompts and parser strict.
- Compare sets by `word_id`, not just histogram counts.

Don’t:
- Don’t upload just because level counts match target distribution.
- Don’t relax parser by reintroducing silent top-up behavior.
- Don’t run `full-fallback` upload mode during iterative campaigns.

## Model Recommendation

- Primary Step5 selector: `openai/gpt-oss-20b` (with strict contract checks).
- Use EuroLLM/Ministral as secondary comparisons, not sole arbiter for final L1 selection.

## Next Improvement Backlog

1. Expand anchor set to >= 400 curated L1 words.
2. Add a negative anchor set for obvious non-L1 words (precision stress-test).
3. Add CI smoke job for `rarity_quality_audit.cjs` on sample fixture CSVs.
