#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/rarity_rebalance_target_distribution.sh \
    --input-csv <path> \
    [--model <id>] \
    [--run-base <slug>] \
    [--runs-dir <path>] \
    [--state-file <path>] \
    [--resume <true|false>] \
    [--final-output-csv <path>] \
    [--batch-size <int>] \
    [--max-tokens <int>] \
    [--timeout-seconds <int>] \
    [--max-retries <int>] \
    [--system-prompt-file <path>] \
    [--user-template-file <path>]

Target distribution (fixed, as requested):
  level1=2500, level2=7500, level3=15000, level4=22698, level5=30000

Sequence (fixed):
  - levels 1+2 -> level 1 target 2500: run 3x
  - levels 2+3 -> level 2 target 7500: run 2x
  - levels 3+4 -> level 3 target 15000: run 2x
  - levels 4+5 -> level 4 target 22698: run 1x
EOF
}

require_file() {
  local path="$1"
  if [[ ! -f "$path" ]]; then
    echo "Missing file: $path" >&2
    exit 1
  fi
}

sanitize_slug() {
  local raw="$1"
  local cleaned
  cleaned="$(echo "$raw" | tr '[:upper:]-' '[:lower:]_' | tr -cd 'a-z0-9_')"
  cleaned="${cleaned:0:40}"
  if [[ -z "$cleaned" ]]; then
    cleaned="rebalance_run"
  fi
  echo "$cleaned"
}

count_total_words() {
  local csv_path="$1"
  awk -F',' 'NR>1 { c++ } END { print c+0 }' "$csv_path"
}

build_step_slug() {
  local step="$1"
  local from_low="$2"
  local from_high="$3"
  local to_level="$4"
  local run_suffix="$RUN_BASE"
  if (( ${#run_suffix} > 24 )); then
    run_suffix="${run_suffix: -24}"
  fi
  sanitize_slug "s${step}_${from_low}${from_high}to${to_level}_${run_suffix}"
}

get_level_count() {
  local csv_path="$1"
  local level="$2"
  awk -F',' -v want="$level" '
    NR==1 {
      col=0
      for (i=1; i<=NF; i++) {
        h=$i
        gsub(/"/, "", h)
        if (h=="final_level") col=i
        if (h=="rarity_level" && col==0) col=i
      }
      next
    }
    {
      v=$col
      gsub(/"/, "", v)
      if ((v+0) == want) cnt++
    }
    END { print cnt+0 }
  ' "$csv_path"
}

print_distribution() {
  local csv_path="$1"
  local c1 c2 c3 c4 c5
  c1="$(get_level_count "$csv_path" 1)"
  c2="$(get_level_count "$csv_path" 2)"
  c3="$(get_level_count "$csv_path" 3)"
  c4="$(get_level_count "$csv_path" 4)"
  c5="$(get_level_count "$csv_path" 5)"
  echo "distribution=[1:$c1 2:$c2 3:$c3 4:$c4 5:$c5]"
}

compute_ratio() {
  local target="$1"
  local pool="$2"
  awk -v t="$target" -v p="$pool" 'BEGIN { printf "%.12f", t / p }'
}

assert_ratio_range() {
  local ratio="$1"
  awk -v r="$ratio" 'BEGIN { exit !(r >= 0.01 && r <= 0.99) }'
}

step_output_csv() {
  local step="$1"
  echo "${RUNS_DIR}/${RUN_BASE}_step${step}.csv"
}

write_state() {
  local last_completed_step="$1"
  local current_csv="$2"
  local tmp="${STATE_FILE}.tmp"
  {
    printf 'last_completed_step\t%s\n' "$last_completed_step"
    printf 'current_csv\t%s\n' "$current_csv"
    printf 'run_base\t%s\n' "$RUN_BASE"
    printf 'model\t%s\n' "$MODEL"
  } > "$tmp"
  mv "$tmp" "$STATE_FILE"
}

load_state() {
  LAST_COMPLETED_STEP="$(awk -F'\t' '$1=="last_completed_step"{print $2}' "$STATE_FILE" | tail -n1)"
  CURRENT_CSV="$(awk -F'\t' '$1=="current_csv"{print $2}' "$STATE_FILE" | tail -n1)"
  if [[ -z "${LAST_COMPLETED_STEP:-}" || -z "${CURRENT_CSV:-}" ]]; then
    echo "State file is invalid: $STATE_FILE" >&2
    exit 1
  fi
  if [[ ! "$LAST_COMPLETED_STEP" =~ ^[0-9]+$ ]]; then
    echo "State file has invalid step value: $LAST_COMPLETED_STEP" >&2
    exit 1
  fi
  if [[ ! -f "$CURRENT_CSV" ]]; then
    echo "State file points to missing CSV: $CURRENT_CSV" >&2
    exit 1
  fi
}

discover_completed_steps_from_outputs() {
  local highest=0
  local csv="$INPUT_CSV"
  local step path
  for step in $(seq 1 "$TOTAL_STEPS"); do
    path="$(step_output_csv "$step")"
    if [[ -f "$path" ]]; then
      highest="$step"
      csv="$path"
    else
      break
    fi
  done
  LAST_COMPLETED_STEP="$highest"
  CURRENT_CSV="$csv"
}

run_step() {
  local step="$1"
  local from_low="$2"
  local from_high="$3"
  local to_level="$4"
  local target_to_level="$5"

  local count_low count_high pool ratio step_slug next_csv
  next_csv="$(step_output_csv "$step")"

  if [[ "$RESUME" == "true" && "$step" -le "$LAST_COMPLETED_STEP" ]]; then
    if [[ ! -f "$next_csv" ]]; then
      echo "[step $step] resume state says completed, but output is missing: $next_csv" >&2
      exit 1
    fi
    CURRENT_CSV="$next_csv"
    echo "[step $step] resume skip -> $CURRENT_CSV"
    return
  fi

  count_low="$(get_level_count "$CURRENT_CSV" "$from_low")"
  count_high="$(get_level_count "$CURRENT_CSV" "$from_high")"
  pool=$((count_low + count_high))

  if (( pool <= 1 )); then
    echo "[step $step] pool too small for levels $from_low+$from_high: $pool" >&2
    exit 1
  fi
  if (( target_to_level < 1 || target_to_level >= pool )); then
    echo "[step $step] invalid target=$target_to_level for pool=$pool (levels $from_low+$from_high)" >&2
    exit 1
  fi

  ratio="$(compute_ratio "$target_to_level" "$pool")"
  if ! assert_ratio_range "$ratio"; then
    echo "[step $step] ratio out of supported range 0.01..0.99: ratio=$ratio target=$target_to_level pool=$pool" >&2
    exit 1
  fi

  step_slug="$(build_step_slug "$step" "$from_low" "$from_high" "$to_level")"

  echo
  echo "========== STEP $step =========="
  echo "input_csv=$CURRENT_CSV"
  echo "output_csv=$next_csv"
  echo "run_slug=$step_slug"
  echo "transition=${from_low}-${from_high}->${to_level}"
  echo "pool=${pool} (l${from_low}=${count_low}, l${from_high}=${count_high})"
  echo "target_l${to_level}=${target_to_level} ratio=${ratio}"
  echo "$(print_distribution "$CURRENT_CSV")"

  ./gradlew rarityStep5Rebalance --args="--run ${step_slug} --model ${MODEL} --step2-csv ${CURRENT_CSV} --output-csv ${next_csv} --from-level ${from_low} --from-level-high ${from_high} --to-level ${to_level} --batch-size ${BATCH_SIZE} --lower-ratio ${ratio} --max-tokens ${MAX_TOKENS} --timeout-seconds ${TIMEOUT_SECONDS} --max-retries ${MAX_RETRIES} --system-prompt-file ${SYSTEM_PROMPT_FILE} --user-template-file ${USER_TEMPLATE_FILE}"

  CURRENT_CSV="$next_csv"
  LAST_COMPLETED_STEP="$step"
  write_state "$LAST_COMPLETED_STEP" "$CURRENT_CSV"
  echo "[step $step] done"
  echo "$(print_distribution "$CURRENT_CSV")"
}

MODEL="openai/gpt-oss-20b"
RUN_BASE="rb$(date +%m%d_%H%M)"
RUNS_DIR="build/rarity/runs"
STATE_FILE=""
RESUME="true"
FINAL_OUTPUT_CSV=""
BATCH_SIZE=600
MAX_TOKENS=1200
TIMEOUT_SECONDS=120
MAX_RETRIES=2
SYSTEM_PROMPT_FILE="docs/rarity-prompts/rebalance_system_prompt_ro.txt"
USER_TEMPLATE_FILE="docs/rarity-prompts/rebalance_user_prompt_template_ro.txt"
INPUT_CSV=""
LAST_COMPLETED_STEP=0
TOTAL_STEPS=8

while [[ $# -gt 0 ]]; do
  case "$1" in
    --input-csv) INPUT_CSV="$2"; shift 2 ;;
    --model) MODEL="$2"; shift 2 ;;
    --run-base) RUN_BASE="$2"; shift 2 ;;
    --runs-dir) RUNS_DIR="$2"; shift 2 ;;
    --state-file) STATE_FILE="$2"; shift 2 ;;
    --resume) RESUME="$2"; shift 2 ;;
    --no-resume) RESUME="false"; shift 1 ;;
    --final-output-csv) FINAL_OUTPUT_CSV="$2"; shift 2 ;;
    --batch-size) BATCH_SIZE="$2"; shift 2 ;;
    --max-tokens) MAX_TOKENS="$2"; shift 2 ;;
    --timeout-seconds) TIMEOUT_SECONDS="$2"; shift 2 ;;
    --max-retries) MAX_RETRIES="$2"; shift 2 ;;
    --system-prompt-file) SYSTEM_PROMPT_FILE="$2"; shift 2 ;;
    --user-template-file) USER_TEMPLATE_FILE="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ -z "$INPUT_CSV" ]]; then
  echo "--input-csv is required" >&2
  usage
  exit 1
fi

require_file "$INPUT_CSV"
require_file "$SYSTEM_PROMPT_FILE"
require_file "$USER_TEMPLATE_FILE"
mkdir -p "$RUNS_DIR"
RUN_BASE="$(sanitize_slug "$RUN_BASE")"
if [[ -z "$STATE_FILE" ]]; then
  STATE_FILE="${RUNS_DIR}/${RUN_BASE}.rebalance.state"
fi
if [[ "$RESUME" != "true" && "$RESUME" != "false" ]]; then
  echo "--resume must be true or false (received: $RESUME)" >&2
  exit 1
fi

# Ensure Gradle uses the intended JDK (this repo expects JDK 21).
if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
fi

CURRENT_CSV="$INPUT_CSV"

if [[ "$RESUME" == "true" ]]; then
  if [[ -f "$STATE_FILE" ]]; then
    echo "Resuming from state file: $STATE_FILE"
    load_state
  else
    discover_completed_steps_from_outputs
    if [[ "$LAST_COMPLETED_STEP" -gt 0 ]]; then
      echo "Resume discovery: found completed steps up to $LAST_COMPLETED_STEP"
      write_state "$LAST_COMPLETED_STEP" "$CURRENT_CSV"
    fi
  fi
fi

echo "Starting chained rarity rebalancing campaign"
echo "model=$MODEL"
echo "run_base=$RUN_BASE"
echo "resume=$RESUME state_file=$STATE_FILE last_completed_step=$LAST_COMPLETED_STEP"
echo "batch_size=$BATCH_SIZE"
echo "max_tokens=$MAX_TOKENS timeout_seconds=$TIMEOUT_SECONDS max_retries=$MAX_RETRIES"
echo "current_csv=$CURRENT_CSV"
echo "$(print_distribution "$CURRENT_CSV")"

TARGET_L1=2500
TARGET_L2=7500
TARGET_L3=15000
TARGET_L5=30000
TOTAL_WORDS="$(count_total_words "$CURRENT_CSV")"
TARGET_L4=$((TOTAL_WORDS - TARGET_L1 - TARGET_L2 - TARGET_L3 - TARGET_L5))
if (( TARGET_L4 < 1 )); then
  echo "Invalid target distribution for total=$TOTAL_WORDS: computed level4 target is $TARGET_L4" >&2
  exit 1
fi
echo "target_distribution=[1:$TARGET_L1 2:$TARGET_L2 3:$TARGET_L3 4:$TARGET_L4 5:$TARGET_L5] total=$TOTAL_WORDS"

step_idx=1
for _ in 1 2 3; do
  run_step "$step_idx" 1 2 1 "$TARGET_L1"
  step_idx=$((step_idx + 1))
done
for _ in 1 2; do
  run_step "$step_idx" 2 3 2 "$TARGET_L2"
  step_idx=$((step_idx + 1))
done
for _ in 1 2; do
  run_step "$step_idx" 3 4 3 "$TARGET_L3"
  step_idx=$((step_idx + 1))
done
run_step "$step_idx" 4 5 4 "$TARGET_L4"

if [[ -n "$FINAL_OUTPUT_CSV" ]]; then
  cp "$CURRENT_CSV" "$FINAL_OUTPUT_CSV"
  echo "Final output copied to: $FINAL_OUTPUT_CSV"
else
  echo "Final output CSV: $CURRENT_CSV"
fi

write_state "$LAST_COMPLETED_STEP" "$CURRENT_CSV"
echo "Final $(print_distribution "$CURRENT_CSV")"
