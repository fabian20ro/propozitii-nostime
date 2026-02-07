#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "Usage: $0 <failed-jsonl> <base-csv> <output-csv>" >&2
  exit 1
fi

FAILED_JSONL="$1"
BASE_CSV="$2"
OUTPUT_CSV="$3"

if [[ ! -f "$FAILED_JSONL" ]]; then
  echo "Failed JSONL not found: $FAILED_JSONL" >&2
  exit 1
fi

if [[ ! -f "$BASE_CSV" ]]; then
  echo "Base CSV not found: $BASE_CSV" >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "Missing dependency: jq" >&2
  exit 1
fi

mkdir -p "$(dirname "$OUTPUT_CSV")"

tmp_ids="$(mktemp)"
cleanup() {
  rm -f "$tmp_ids"
}
trap cleanup EXIT

jq -r '.word_id // empty' "$FAILED_JSONL" \
  | awk '/^[0-9]+$/' \
  | sort -n -u > "$tmp_ids"

awk -F',' '
  NR==FNR { ids[$1]=1; next }
  NR==1 { print; next }
  {
    id=$1
    gsub(/"/, "", id)
    if (id in ids) print
  }
' "$tmp_ids" "$BASE_CSV" > "$OUTPUT_CSV"

rows="$(($(wc -l < "$OUTPUT_CSV") - 1))"
echo "Wrote retry input CSV: $OUTPUT_CSV (rows=$rows)"
