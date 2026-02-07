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
