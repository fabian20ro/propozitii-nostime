# Vercel Lambda Fallback

This project deploys a Vercel Serverless Function from `api/all.ts`.

It is not a proxy to Render. It generates sentences directly from Supabase and returns the same JSON shape as backend `GET /api/all`.

## Files Used In Deployment

- `api/all.ts` - main Vercel handler (`export default async function handler(...)`)
- `vercel.json` - function limits + route headers
- `package.json` - dependencies used by function runtime:
  - `@supabase/supabase-js`
  - `typescript` and `vitest` for local checks

## Runtime Contract

Endpoint:
- `GET /api/all?minRarity=1..5&rarity=1..5`

Response keys (all strings):
- `haiku`, `distih`, `comparison`, `definition`, `tautogram`, `mirror`

CORS:
- Handler sets:
  - `Access-Control-Allow-Origin: <allowed origin>`
  - `Vary: Origin`
  - `Access-Control-Allow-Methods: GET, OPTIONS`
  - `Access-Control-Allow-Headers: Content-Type`

Frontend coupling:
- `frontend/app.js` fallback base is `https://propozitii-nostime.vercel.app/api`

## Required Environment Variables (Vercel Project)

- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY` (preferred)

Optional:
- `ALLOWED_ORIGINS` (comma-separated origin allowlist, defaults to `https://fabian20ro.github.io`)
- `ALLOW_SUPABASE_SERVICE_ROLE_FALLBACK=true` (explicit opt-in only)

If required env vars are missing or insecure-only fallback is blocked, `api/all.ts` returns:
- HTTP `500`
- JSON error with actionable key guidance.

`SUPABASE_URL` must be the HTTP API URL (`https://<project-ref>.supabase.co`), not a JDBC connection string.

## First-Time Deploy Tutorial (Dashboard)

1. Ensure these files are present on `master`:
   - `api/all.ts`
   - `vercel.json`
   - `package.json`

2. In Vercel, create a new project:
   - `Add New -> Project -> Import Git Repository`
   - select `fabian20ro/propozitii-nostime`

3. Configure project:
   - Framework Preset: `Other`
   - Root Directory: repo root
   - Build Command: leave empty
   - Output Directory: leave empty
   - Install Command: default (`npm install`)

4. Add environment variables in Vercel:
   - `SUPABASE_URL=https://<project-ref>.supabase.co`
   - `SUPABASE_PUBLISHABLE_KEY=<your-sb_publishable-key>`
   - optional: `ALLOWED_ORIGINS=https://fabian20ro.github.io`
   Add them for `Production` (and optionally `Preview`/`Development`).

5. Deploy.

6. Verify after deploy:
   - `https://<your-vercel-domain>/api/all?minRarity=1&rarity=2`
   - expect HTTP `200` and JSON containing all six keys.

7. If using a different Vercel domain, update:
   - `frontend/app.js` -> `FALLBACK_API_BASE`
   Then redeploy frontend (GitHub Pages workflow).

## Smoke Checks

1. Missing env check:
   - temporarily unset env in Preview and call `/api/all`
   - verify clear `500` error message above.

2. Contract check:
   - verify each response key exists and is a string.

3. HTML contract check:
   - verify output still contains dexonline `<a href="https://dexonline.ro/definitie/...">`
   - verify verses use `<br/>`.
