#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

function usageAndExit(code) {
  const msg = `
Usage:
  node scripts/rarity_quality_audit.cjs --candidate-csv <path> [options]

Options:
  --reference-csv <path>            Compare L1 set against reference run and compute Jaccard.
  --anchor-l1-file <path>           Newline-separated L1 anchor words for precision/recall checks.
  --min-l1-jaccard <float>          Fail if computed Jaccard is below threshold.
  --min-anchor-l1-precision <float> Fail if anchor L1 precision is below threshold.
  --min-anchor-l1-recall <float>    Fail if anchor L1 recall is below threshold.
  --help                            Show this help.

Notes:
  - Level column precedence: final_level > rarity_level > median_level.
  - Matching for anchor words is case-insensitive on \`word\`.
`;
  process.stderr.write(msg.trimStart());
  process.stderr.write("\n");
  process.exit(code);
}

function parseArgs(argv) {
  const args = {};
  for (let i = 2; i < argv.length; i++) {
    const key = argv[i];
    if (key === "--help" || key === "-h") usageAndExit(0);
    if (!key.startsWith("--")) usageAndExit(2);
    const val = argv[i + 1];
    if (val == null || val.startsWith("--")) usageAndExit(2);
    args[key.slice(2)] = val;
    i++;
  }
  return args;
}

function parseCsv(text) {
  const rows = [];
  let row = [];
  let cell = "";
  let inQuotes = false;

  for (let i = 0; i < text.length; i++) {
    const ch = text[i];

    if (inQuotes) {
      if (ch === '"') {
        if (i + 1 < text.length && text[i + 1] === '"') {
          cell += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        cell += ch;
      }
      continue;
    }

    if (ch === '"') {
      inQuotes = true;
      continue;
    }

    if (ch === ",") {
      row.push(cell);
      cell = "";
      continue;
    }

    if (ch === "\n") {
      row.push(cell);
      rows.push(row);
      row = [];
      cell = "";
      continue;
    }

    if (ch === "\r") continue;
    cell += ch;
  }

  if (cell.length > 0 || row.length > 0) {
    row.push(cell);
    rows.push(row);
  }

  return rows;
}

function resolveLevelColumn(headers) {
  const precedence = ["final_level", "rarity_level", "median_level"];
  for (const col of precedence) {
    const idx = headers.indexOf(col);
    if (idx >= 0) return { name: col, index: idx };
  }
  throw new Error(
    `CSV missing level column. Expected one of final_level/rarity_level/median_level. Got: ${headers.join(", ")}`
  );
}

function loadRunCsv(csvPath) {
  const raw = fs.readFileSync(csvPath, "utf8");
  const rows = parseCsv(raw);
  if (rows.length < 2) throw new Error(`CSV has no data rows: ${csvPath}`);

  const headers = rows[0];
  const wordIdIdx = headers.indexOf("word_id");
  const wordIdx = headers.indexOf("word");
  if (wordIdIdx < 0 || wordIdx < 0) {
    throw new Error(`CSV must contain word_id and word: ${csvPath}`);
  }
  const levelCol = resolveLevelColumn(headers);

  const distribution = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
  const l1WordIds = new Set();
  const l1Words = new Set();
  let totalRows = 0;

  for (let i = 1; i < rows.length; i++) {
    const r = rows[i];
    if (r.length === 1 && r[0] === "") continue;
    totalRows++;
    const wordId = Number.parseInt(r[wordIdIdx] || "", 10);
    const level = Number.parseInt(r[levelCol.index] || "", 10);
    const word = (r[wordIdx] || "").trim();
    if (!Number.isInteger(wordId)) {
      throw new Error(`Invalid word_id at row ${i + 1} in ${csvPath}`);
    }
    if (!Number.isInteger(level) || level < 1 || level > 5) {
      throw new Error(`Invalid level at row ${i + 1} in ${csvPath}`);
    }
    distribution[level] += 1;
    if (level === 1) {
      l1WordIds.add(wordId);
      if (word !== "") l1Words.add(word.toLowerCase());
    }
  }

  return {
    path: csvPath,
    levelColumn: levelCol.name,
    totalRows,
    distribution,
    l1WordIds,
    l1Words
  };
}

function loadAnchorWords(anchorPath) {
  const raw = fs.readFileSync(anchorPath, "utf8");
  const words = new Set();
  raw.split(/\r?\n/).forEach((line) => {
    const t = line.trim();
    if (t === "" || t.startsWith("#")) return;
    words.add(t.toLowerCase());
  });
  if (words.size === 0) {
    throw new Error(`Anchor file has no usable words: ${anchorPath}`);
  }
  return words;
}

function setIntersectionSize(a, b) {
  let count = 0;
  const [small, large] = a.size <= b.size ? [a, b] : [b, a];
  for (const v of small) {
    if (large.has(v)) count++;
  }
  return count;
}

function ratio(numerator, denominator) {
  if (denominator <= 0) return 0;
  return numerator / denominator;
}

function fmtPct(x) {
  return (x * 100).toFixed(2) + "%";
}

function fmt4(x) {
  return x.toFixed(4);
}

function parseThreshold(args, key) {
  if (!(key in args)) return null;
  const n = Number.parseFloat(args[key]);
  if (!Number.isFinite(n) || n < 0 || n > 1) {
    throw new Error(`Invalid ${key}: expected float 0..1`);
  }
  return n;
}

function main() {
  const args = parseArgs(process.argv);
  if (!args["candidate-csv"]) usageAndExit(2);

  const candidatePath = path.resolve(args["candidate-csv"]);
  const referencePath = args["reference-csv"] ? path.resolve(args["reference-csv"]) : null;
  const anchorPath = args["anchor-l1-file"] ? path.resolve(args["anchor-l1-file"]) : null;

  const minJaccard = parseThreshold(args, "min-l1-jaccard");
  const minAnchorPrecision = parseThreshold(args, "min-anchor-l1-precision");
  const minAnchorRecall = parseThreshold(args, "min-anchor-l1-recall");

  const candidate = loadRunCsv(candidatePath);
  const failures = [];

  process.stdout.write(`candidate_csv=${candidate.path}\n`);
  process.stdout.write(`candidate_level_column=${candidate.levelColumn}\n`);
  process.stdout.write(
    `candidate_distribution=[1:${candidate.distribution[1]} 2:${candidate.distribution[2]} 3:${candidate.distribution[3]} 4:${candidate.distribution[4]} 5:${candidate.distribution[5]}] total=${candidate.totalRows}\n`
  );

  if (referencePath) {
    const reference = loadRunCsv(referencePath);
    const inter = setIntersectionSize(candidate.l1WordIds, reference.l1WordIds);
    const union = candidate.l1WordIds.size + reference.l1WordIds.size - inter;
    const jaccard = ratio(inter, union);
    process.stdout.write(
      `l1_jaccard=${fmt4(jaccard)} intersection=${inter} candidate_l1=${candidate.l1WordIds.size} reference_l1=${reference.l1WordIds.size}\n`
    );
    if (minJaccard != null && jaccard < minJaccard) {
      failures.push(`l1_jaccard ${fmt4(jaccard)} < min ${fmt4(minJaccard)}`);
    }
  }

  if (anchorPath) {
    const anchors = loadAnchorWords(anchorPath);
    const inter = setIntersectionSize(candidate.l1Words, anchors);
    const precision = ratio(inter, candidate.l1Words.size);
    const recall = ratio(inter, anchors.size);
    process.stdout.write(
      `anchor_l1_precision=${fmt4(precision)} (${inter}/${candidate.l1Words.size}) anchor_l1_recall=${fmt4(recall)} (${inter}/${anchors.size})\n`
    );
    if (minAnchorPrecision != null && precision < minAnchorPrecision) {
      failures.push(`anchor_l1_precision ${fmt4(precision)} < min ${fmt4(minAnchorPrecision)}`);
    }
    if (minAnchorRecall != null && recall < minAnchorRecall) {
      failures.push(`anchor_l1_recall ${fmt4(recall)} < min ${fmt4(minAnchorRecall)}`);
    }
  }

  if (failures.length > 0) {
    process.stdout.write("quality_gate=FAIL\n");
    failures.forEach((f) => process.stdout.write(`- ${f}\n`));
    process.exit(1);
  }

  process.stdout.write("quality_gate=PASS\n");
}

main();
