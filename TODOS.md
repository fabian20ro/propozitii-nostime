# TODOS

## Runtime App

### Completed
- [x] Public API supports rarity range filters (`minRarity`, `rarity`) for sentence generation.
- [x] Frontend uses dual-range rarity controls with persistent local storage.
- [x] Render/Vercel `/api/all` parity maintained for all six sentence fields.

### Next Improvements
- [ ] Add explicit contract tests for `minRarity`/`rarity` clamping behavior in both backends.
- [ ] Add a small smoke script for validating Render primary and Vercel fallback parity from one command.
- [ ] Improve health-poll diagnostics in frontend when Render remains cold/unreachable.

### Nice to Have
- [ ] Add lightweight API response-time telemetry for `/api/all` primary vs fallback path.
- [ ] Add a short troubleshooting matrix in `docs/RUNBOOK.md` for Supabase query failures by status code.
