# Rarity Campaign Runbook (77k Words)

This runbook is for full rebuild campaigns where rarity is regenerated from scratch from `build/rarity/step1_words.csv`.

## Defaults for this campaign

- Model A (first pass): `openai/gpt-oss-20b`
- Model B (second pass): `zai-org/glm-4.7-flash`
- Upload mode during iterative runs: `partial` only
- Coverage gate before final merge upload: `>= 99.5%`

## Naming convention

Use unique run slugs and output files for each campaign:

- `campaign_<YYYYMMDD>_a_gptoss20b`
- `campaign_<YYYYMMDD>_b_glm47flash`

Do not reuse old run slugs when restarting a new campaign.

## Recommended Step 2 knobs

- `--batch-size 100` (adaptive: auto-shrinks on failures, grows back on success)
- `--max-tokens 8000` (dynamic: actual value scales up with batch size as `max(batchSize*26+120, maxTokens)`)
- `--timeout-seconds 120`
- `--max-retries 2` for full pass
- `--max-retries 3` for retry subset pass

These recommendations currently match CLI defaults.
Batch size is the *initial* size; `BatchSizeAdapter` adjusts it at runtime based on a sliding window of recent outcomes.

## End-to-end commands

```bash
# 1) Export source words
./gradlew rarityStep1Export

# 2) Model A full pass (repeat command until pending stabilizes)
./gradlew rarityStep2Score --args="--run campaign_20260207_a_gptoss20b --model openai/gpt-oss-20b --base-csv build/rarity/step1_words.csv --output-csv build/rarity/runs/campaign_20260207_a_gptoss20b.csv --batch-size 100 --max-tokens 8000 --timeout-seconds 120 --max-retries 2 --system-prompt-file docs/rarity-prompts/system_prompt_ro.txt --user-template-file docs/rarity-prompts/user_prompt_template_ro.txt"

# 3) Optional targeted retry for unresolved model A words
./scripts/rarity_build_retry_input.sh build/rarity/failed_batches/campaign_20260207_a_gptoss20b.failed.jsonl build/rarity/step1_words.csv build/rarity/retry_inputs/campaign_20260207_a_retry.csv
./gradlew rarityStep2Score --args="--run campaign_20260207_a_gptoss20b_retry --model openai/gpt-oss-20b --base-csv build/rarity/step1_words.csv --input build/rarity/retry_inputs/campaign_20260207_a_retry.csv --output-csv build/rarity/runs/campaign_20260207_a_gptoss20b.csv --batch-size 100 --max-tokens 8000 --timeout-seconds 120 --max-retries 3 --system-prompt-file docs/rarity-prompts/system_prompt_ro.txt --user-template-file docs/rarity-prompts/user_prompt_template_ro.txt"

# 4) Early upload from model A (partial upload only)
./gradlew rarityStep4Upload --args="--final-csv build/rarity/runs/campaign_20260207_a_gptoss20b.csv"

# 5) Model B full pass
./gradlew rarityStep2Score --args="--run campaign_20260207_b_glm47flash --model zai-org/glm-4.7-flash --base-csv build/rarity/step1_words.csv --output-csv build/rarity/runs/campaign_20260207_b_glm47flash.csv --batch-size 100 --max-tokens 8000 --timeout-seconds 120 --max-retries 2 --system-prompt-file docs/rarity-prompts/system_prompt_ro.txt --user-template-file docs/rarity-prompts/user_prompt_template_ro.txt"

# 6) Optional targeted retry for unresolved model B words
./scripts/rarity_build_retry_input.sh build/rarity/failed_batches/campaign_20260207_b_glm47flash.failed.jsonl build/rarity/step1_words.csv build/rarity/retry_inputs/campaign_20260207_b_retry.csv
./gradlew rarityStep2Score --args="--run campaign_20260207_b_glm47flash_retry --model zai-org/glm-4.7-flash --base-csv build/rarity/step1_words.csv --input build/rarity/retry_inputs/campaign_20260207_b_retry.csv --output-csv build/rarity/runs/campaign_20260207_b_glm47flash.csv --batch-size 100 --max-tokens 8000 --timeout-seconds 120 --max-retries 3 --system-prompt-file docs/rarity-prompts/system_prompt_ro.txt --user-template-file docs/rarity-prompts/user_prompt_template_ro.txt"

# 7) Compare + final merge
./gradlew rarityStep3Compare --args="--run-a-csv build/rarity/runs/campaign_20260207_a_gptoss20b.csv --run-b-csv build/rarity/runs/campaign_20260207_b_glm47flash.csv --output-csv build/rarity/step3_comparison.csv --outliers-csv build/rarity/step3_outliers.csv --outlier-threshold 2"
./gradlew rarityStep4Upload --args="--final-csv build/rarity/step3_comparison.csv"
```

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

- **JSON repair**: truncated/malformed LM output is auto-repaired before parsing (trailing decimals, unclosed structures, comments, trailing commas)
- **Partial extraction + retry on unresolved**: parsed rows are kept, and unresolved rows are retried separately in-process
- **`word_id` matching first**: parser pairs results by `word_id` before fallback matching on `word/type`
- **Fuzzy matching**: Romanian diacritical misspellings from LM are accepted (Levenshtein distance <= 2 on normalized forms)
- **Adaptive batching**: batch size shrinks after weak outcomes (floor=`max(5, initial/5)`), grows back after sustained success (cap=initial)
- **`response_format` capability cache**: after one unsupported response, the run disables `response_format` for all following requests
- **Model crash backoff**: linear delay (10s * attempt) when LMStudio reports model crash
- **Metrics**: end-of-run summary with WPM, ETA, error breakdown by category

## Safety rules

- Never run Step 4 with `--mode full-fallback` during iterative scoring.
- Keep one writer per `--output-csv`; Step 2 lock is per output file.
- Prefer rerunning the same command for resume semantics instead of editing run CSV by hand.
