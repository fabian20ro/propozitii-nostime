# Rarity Campaign Runbook (77k Words)

This runbook is for full rebuild campaigns where rarity is regenerated from scratch from `build/rarity/step1_words.csv`.

## Defaults for this campaign

- Model A (first pass): `openai/gpt-oss-20b`
- Model B (second pass): `zai-org/glm-4.7-flash`
- Model C (optional): `mlx-community/EuroLLM-22B-Instruct-2512-mlx-4bit` (alias supported: `eurollm-22b-instruct-2512-mlx`)
- Upload mode during iterative runs: `partial` only
- Coverage gate before final merge upload: `>= 99.5%`

If model B does not fit memory on your machine, replace it with `ministral-3-8b-instruct-2512-mixed-8-6-bit` or skip directly to model C.

## Naming convention

Use unique run slugs and output files for each campaign:

- `campaign_<YYYYMMDD>_a_gptoss20b`
- `campaign_<YYYYMMDD>_b_glm47flash`
- `campaign_<YYYYMMDD>_c_eurollm22b`

Do not reuse old run slugs when restarting a new campaign.

## Recommended Step 2 knobs

- `--batch-size 50` (adaptive: auto-shrinks on failures, grows back on success)
- `--max-tokens 8000` (dynamic ceiling: per-request budget is estimated from batch size and capped by `--max-tokens`)
- `--timeout-seconds 120`
- `--max-retries 2` for full pass
- `--max-retries 3` for retry subset pass

These recommendations currently match CLI defaults.
Batch size is the *initial* size; `BatchSizeAdapter` adjusts it at runtime based on a sliding window of recent outcomes.

Model parameter defaults are in code and can be tuned per model without touching Step 2 logic:
- `src/main/kotlin/scrabble/phrases/tools/rarity/lmstudio/LmModelDefaultsGptOss20b.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/lmstudio/LmModelDefaultsGlm47Flash.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/lmstudio/LmModelDefaultsMinistral38b.kt`
- `src/main/kotlin/scrabble/phrases/tools/rarity/lmstudio/LmModelDefaultsEuroLlm22b.kt`

Current safety baseline for all model profiles:
- deterministic decoding (`temperature=0`, `top_p=1.0`)
- capped reasoning controls per model profile
- `response_format` auto-disable when `json_schema` output quality collapses (high unresolved ratio)

## End-to-end commands

```bash
# 1) Export source words
./gradlew rarityStep1Export

# 2) Model A full pass (repeat command until pending stabilizes)
./gradlew rarityStep2Score --args="--run campaign_20260207_a_gptoss20b --model openai/gpt-oss-20b --base-csv build/rarity/step1_words.csv --output-csv build/rarity/runs/campaign_20260207_a_gptoss20b.csv --batch-size 50 --max-tokens 8000 --timeout-seconds 120 --max-retries 2 --system-prompt-file docs/rarity-prompts/system_prompt_ro.txt --user-template-file docs/rarity-prompts/user_prompt_template_ro.txt"

# 3) Optional targeted retry for unresolved model A words
./scripts/rarity_build_retry_input.sh build/rarity/failed_batches/campaign_20260207_a_gptoss20b.failed.jsonl build/rarity/step1_words.csv build/rarity/retry_inputs/campaign_20260207_a_retry.csv
./gradlew rarityStep2Score --args="--run campaign_20260207_a_gptoss20b_retry --model openai/gpt-oss-20b --base-csv build/rarity/step1_words.csv --input build/rarity/retry_inputs/campaign_20260207_a_retry.csv --output-csv build/rarity/runs/campaign_20260207_a_gptoss20b.csv --batch-size 50 --max-tokens 8000 --timeout-seconds 120 --max-retries 3 --system-prompt-file docs/rarity-prompts/system_prompt_ro.txt --user-template-file docs/rarity-prompts/user_prompt_template_ro.txt"

# 4) Early upload from model A (partial upload only)
./gradlew rarityStep4Upload --args="--final-csv build/rarity/runs/campaign_20260207_a_gptoss20b.csv"

# 5) Model B full pass
./gradlew rarityStep2Score --args="--run campaign_20260207_b_glm47flash --model zai-org/glm-4.7-flash --base-csv build/rarity/step1_words.csv --output-csv build/rarity/runs/campaign_20260207_b_glm47flash.csv --batch-size 50 --max-tokens 8000 --timeout-seconds 120 --max-retries 2 --system-prompt-file docs/rarity-prompts/system_prompt_ro.txt --user-template-file docs/rarity-prompts/user_prompt_template_ro.txt"

# 6) Optional targeted retry for unresolved model B words
./scripts/rarity_build_retry_input.sh build/rarity/failed_batches/campaign_20260207_b_glm47flash.failed.jsonl build/rarity/step1_words.csv build/rarity/retry_inputs/campaign_20260207_b_retry.csv
./gradlew rarityStep2Score --args="--run campaign_20260207_b_glm47flash_retry --model zai-org/glm-4.7-flash --base-csv build/rarity/step1_words.csv --input build/rarity/retry_inputs/campaign_20260207_b_retry.csv --output-csv build/rarity/runs/campaign_20260207_b_glm47flash.csv --batch-size 50 --max-tokens 8000 --timeout-seconds 120 --max-retries 3 --system-prompt-file docs/rarity-prompts/system_prompt_ro.txt --user-template-file docs/rarity-prompts/user_prompt_template_ro.txt"

# 7) Optional model C (EuroLLM)
./gradlew rarityStep2Score --args="--run campaign_20260207_c_eurollm22b --model mlx-community/EuroLLM-22B-Instruct-2512-mlx-4bit --base-csv build/rarity/step1_words.csv --output-csv build/rarity/runs/campaign_20260207_c_eurollm22b.csv --batch-size 50 --max-tokens 8000 --timeout-seconds 120 --max-retries 2 --system-prompt-file docs/rarity-prompts/system_prompt_ro.txt --user-template-file docs/rarity-prompts/user_prompt_template_ro.txt"

# 8) Compare + final merge (3-run, user-targeted merge behavior)
./gradlew rarityStep3Compare --args="--run-a-csv build/rarity/runs/campaign_20260207_a_gptoss20b.csv --run-b-csv build/rarity/runs/campaign_20260207_b_glm47flash.csv --run-c-csv build/rarity/runs/campaign_20260207_c_eurollm22b.csv --merge-strategy any-extremes --output-csv build/rarity/step3_comparison.csv --outliers-csv build/rarity/step3_outliers.csv --outlier-threshold 2"
./gradlew rarityStep4Upload --args="--final-csv build/rarity/step3_comparison.csv"

# 9) Optional step5 rebalance on a Step2 CSV, before upload
# mode A: from=3,to=2 => ~1/3 become level 2, rest stay 3
./gradlew rarityStep5Rebalance --args="--run campaign_20260208_rebalance_3_to_2 --model openai/gpt-oss-20b --step2-csv build/rarity/runs/campaign_20260207_a_gptoss20b.csv --output-csv build/rarity/runs/campaign_20260207_a_gptoss20b.rebalanced.csv --from-level 3 --to-level 2 --batch-size 60 --lower-ratio 0.3333 --max-tokens 8000 --timeout-seconds 120 --max-retries 2 --system-prompt-file docs/rarity-prompts/rebalance_system_prompt_ro.txt --user-template-file docs/rarity-prompts/rebalance_user_prompt_template_ro.txt"
# mode B: from=2,to=2 => ~1/3 stay 2, rest (~2/3) move to 3
./gradlew rarityStep5Rebalance --args="--run campaign_20260208_rebalance_2_split --model openai/gpt-oss-20b --step2-csv build/rarity/runs/campaign_20260207_a_gptoss20b.csv --output-csv build/rarity/runs/campaign_20260207_a_gptoss20b.rebalanced.csv --from-level 2 --to-level 2 --batch-size 60 --lower-ratio 0.3333 --max-tokens 8000 --timeout-seconds 120 --max-retries 2"
# mode C: from=4,to=4 => equal split (batch 60 -> 30 remain 4, 30 move to 5)
./gradlew rarityStep5Rebalance --args="--run campaign_20260208_rebalance_4_equal_split --model openai/gpt-oss-20b --step2-csv build/rarity/runs/campaign_20260207_a_gptoss20b.csv --output-csv build/rarity/runs/campaign_20260207_a_gptoss20b.rebalanced.csv --from-level 4 --to-level 4 --batch-size 60 --lower-ratio 0.5 --max-tokens 8000 --timeout-seconds 120 --max-retries 2"
# mode D: pair split across two consecutive buckets (2 and 3), 25% -> 2, 75% -> 3
./gradlew rarityStep5Rebalance --args="--run campaign_20260208_rebalance_2_3_pair_25_75 --model openai/gpt-oss-20b --step2-csv build/rarity/runs/campaign_20260207_a_gptoss20b.csv --output-csv build/rarity/runs/campaign_20260207_a_gptoss20b.rebalanced_pair.csv --from-level 2 --from-level-high 3 --to-level 2 --batch-size 60 --lower-ratio 0.25 --max-tokens 8000 --timeout-seconds 120 --max-retries 2"
# equivalent transitions syntax:
# --transitions "2-3:2"
```

Step5 notes:
- Input is explicitly a Step2-like CSV via `--step2-csv` (expects `word_id,word,type` plus level column).
- Alias `--input-csv` is accepted to plug step5 after other local pipeline outputs.
- `--lower-ratio` accepts `0.01..0.99`; for two-level transitions this maps to `target_level` share vs companion share.
- `--from-level-high` enables pair mode with consecutive buckets (`from-level` + `from-level-high`), for example `2+3`.
- Transition string supports both single-source and pair-source tokens:
  - single: `3:2` (source level 3 only)
  - pair: `2-3:2` (source levels 2 and 3 together, target is one of them)
- In pair mode, each batch is sampled stratified by the initial source mix (for example, initial 25/75 keeps ~25/75 source sampling per batch).
- Level source precedence is `final_level`, then `rarity_level`, then `median_level`.
- Each `word_id` is processed at most once per step5 run, even if later transitions would match.
- Step 5 prompt mode is sparse: LM returns only the `target_level` subset (`TARGET_COUNT` words), and the pipeline auto-assigns non-returned words to the companion level.
- Multi-transition mode is available via `--transitions "2:1,3:2,4:3"`.
- Step 5 is loop-safe: you can feed the previous Step 5 output back into a new Step 5 run.
- Step 5 logs switched words only to `build/rarity/rebalance/switched_words/<run>.switched.jsonl` (`previous_level != new_level`).
- Step 5 writes per-batch resume checkpoints to `build/rarity/rebalance/checkpoints/<run>.checkpoint.jsonl`; rerun the same command with the same `--run` to resume after interruption.

Pipeline integration patterns:
- Per-model normalization before merge:
  - `step2(modelA) -> step5(modelA) -> step2(modelB) -> step5(modelB) -> step3(compare rebalanced runs) -> step4`
- Final-only normalization:
  - `step2(*) -> step3 -> step5(on step3_comparison.csv) -> step4`

## Verification gates

Coverage:

```bash
# scored IDs in a run CSV (excluding header)
RUN_CSV=build/rarity/runs/campaign_20260207_a_gptoss20b.csv
TOTAL=77698
SCORED=$(($(wc -l < "$RUN_CSV") - 1))
echo "coverage=$(awk -v s="$SCORED" -v t="$TOTAL" 'BEGIN{printf \"%.4f\", (s/t)*100}')% scored=$SCORED total=$TOTAL"
```

Level distribution sanity:

```bash
awk -F',' 'NR>1 {gsub(/"/,"",$4); c[$4]++} END {for (k in c) print k, c[k]}' build/rarity/runs/campaign_20260207_a_gptoss20b.csv | sort -n
```

Outlier volume after step 3:

```bash
echo "outliers=$(($(wc -l < build/rarity/step3_outliers.csv) - 1))"
```

Step 2 state sanity:

```bash
cat build/rarity/runs/campaign_20260207_a_gptoss20b.state.json
# `pending` is unresolved words remaining after the run (not the initial pending input size).
```

## Step 2 resilience features

- **Recursion depth guard**: batch split/retry is capped at depth 10 to prevent unbounded recursion; exceeded words are logged to failed log
- **JSON repair**: truncated/malformed LM output is auto-repaired before parsing (trailing decimals, unclosed structures, comments, trailing commas)
- **Partial extraction + retry on unresolved**: parsed rows are kept, and unresolved rows are retried separately in-process
- **Malformed item salvage**: if one object in the returned JSON array is invalid, valid sibling objects are still kept and only unresolved words are retried
- **`word_id` matching first**: parser pairs results by `word_id` before fallback matching on `word/type`
- **Fuzzy matching**: Romanian diacritical misspellings from LM are accepted (Levenshtein distance <= 2 on normalized forms)
- **Adaptive batching**: batch size shrinks after weak outcomes (floor=`max(5, initial/5)`), grows back after sustained success (cap=initial)
- **`response_format` capability cache**: after one unsupported response, the run disables `response_format` for all following requests (tracked via `CapabilityState`: JSON_OBJECT -> JSON_SCHEMA -> NONE)
- **Empty-array guard for JSON schema**: if a model keeps returning `[]` under `json_schema` (0 parsed nodes), Step 2 disables `response_format` for the rest of the run and continues with prompt-only JSON parsing
- **Partial-schema quality guard**: if a `json_schema` response returns too few valid items for the batch (unresolved >= 20%), Step 2 disables `response_format` for the rest of the run
- **Per-batch schema cardinality**: when `json_schema` is active, schema now requires exactly one output item per input item (`minItems=maxItems=batch_size`)
- **reasoning-control capability cache** (GLM profile): after one unsupported `reasoning_effort`/`chat_template_kwargs`, the run disables those controls for all following requests (tracked via `CapabilityState`)
- **Simple JSON contract**: prompts request a plain top-level array; parser still accepts array plus object envelopes like `results`/`items`/`data`
- **Model crash backoff**: linear delay (10s * attempt) when LMStudio reports model crash
- **Metrics**: end-of-run summary with WPM, ETA, error breakdown by category

## Step 3 merge strategy

- Default `--merge-strategy median`: keeps legacy behavior (`final_level = median_level`).
- Optional `--merge-strategy any-extremes` (useful with 3 runs):
  - if any model predicts `1` -> final `1`
  - else if median is `>=3` and any model predicts `2` -> final `2`
  - else if median is `3` or `4` and any model predicts `5` -> final `5`
  - else fallback to median

## Safety rules

- Never run Step 4 with `--mode full-fallback` during iterative scoring.
- Keep one writer per `--output-csv`; Step 2 lock is per output file.
- Prefer rerunning the same command for resume semantics instead of editing run CSV by hand.
