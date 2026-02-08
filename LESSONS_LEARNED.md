# LESSONS_LEARNED

## Update Rule
- Append one short bullet whenever a blocker appears or behavior is different than expected.

## Entries
- 2026-02-07: `./gradlew test` failed locally because Testcontainers could not start Ryuk with Colima Docker socket mount (`operation not supported`).
- 2026-02-07: Full integration test reliability depends on local Docker/Testcontainers setup; use unit-test subsets for quick validation when container runtime is broken.
- 2026-02-07: Restricting generation to only the top 5000 common words is likely to break rhyme/prefix constraints in `Couplet`, `Mirror`, and `Tautogram`.
- 2026-02-07: `AGENTS.md` should stay minimal as an activation/index file; `CLAUDE.md` is only a pointer to `AGENTS.md`.
- 2026-02-07: LMStudio OpenAI-compatible `/v1/chat/completions` can reject `response_format: {"type":"json_object"}` with HTTP 400; Step 2 must fallback to plain text JSON prompting when this happens.
- 2026-02-07: In this setup, Java `HttpClient` calls to `127.0.0.1:1234` timed out while `curl` worked; use direct `HttpURLConnection` + explicit timeouts for LMStudio calls.
- 2026-02-07: Legacy `step4Upload` full update behavior can look like "lost data" on partial CSV input because missing `word_id` rows become `fallback_4`.
- 2026-02-07: `step2` can lose previously scored rows if multiple processes write the same run CSV and a final rewrite runs from stale in-memory state; use an exclusive file lock and merge latest on-disk rows before rewrite.
- 2026-02-07: Silent row skipping in CSV loading masks corruption and can shrink resume state; fail fast on malformed rows instead of ignoring them.
- 2026-02-07: `step4Upload` now defaults to partial mode; `--mode full-fallback` is explicit to avoid accidental global fallback writes.
- 2026-02-07: API/frontend naming drift caused confusion; standardize on `rarity`/`raritate` (query param, slider IDs/localStorage key, docs, tests) and avoid legacy term mixing.
- 2026-02-07: Keeping rarity logic modular (`RarityStep1..4`, `RunCsvRepository`, `UploadMarkerWriter`, `LmStudioClient`) makes overwrite/resume failures easier to reason about than a monolithic pipeline file.
- 2026-02-07: Docs must track operational defaults (`step4` partial mode, Step2 lock + guarded rewrite) or operators can misinterpret partial outputs as data loss.
- 2026-02-07: Run-scoped `response_format` fallback removes repeated HTTP 400 overhead, but gpt-oss can still emit truncated/malformed JSON that forces split retries and shrinks effective batch size.
- 2026-02-08: Running `step4Upload` markers on the same run CSV before `step2` final rewrite can break resume/rewrite due mixed column counts (9 vs 13); use a separate upload copy or wait for step2 to fully finish.
- 2026-02-07: `JsonRepair` must run close-structures BEFORE remove-trailing-commas; otherwise a trailing comma at truncation point (`2,`) becomes `2,}` which still has the comma.
- 2026-02-07: LM diacritical misspellings (e.g. `abreviațiune` -> `abrevițiune`) are not just diacritics swaps but character deletions; pure normalization is insufficient, Levenshtein distance <= 2 on normalized forms is needed.
- 2026-02-07: When recording metrics in both a parser and its caller, double-counting inflates batch counts; record only at one level (prefer the caller).
- 2026-02-08: Large Step 2 batches become stable only if LM output is matched by `word_id`; relying on positional parsing makes partial/truncated JSON collapse effective batch size.
- 2026-02-08: Treating partial parses as success and retrying only unresolved rows in-process preserves throughput better than immediately splitting whole batches.
- 2026-02-08: Adaptive batch control should use success ratio (not strict all-or-nothing) and a higher floor (`max(5, initial/5)`) to avoid long runs dominated by tiny batches.
- 2026-02-08: Step 2 append logic must serialize against existing CSV headers; otherwise adding Step 4 marker columns can corrupt resumed writes even with lock/guard protections.
- 2026-02-08: Current practical Step 2 knobs for this repo are `--batch-size 100` + `--max-tokens 8000`; lower defaults were a major contributor to 12h full-run time.
- 2026-02-08: Step 2 completion state must report `pending` as unresolved-after-run (`failed`) rather than the initial pending input size, otherwise operators get false completion signals.
