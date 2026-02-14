# Vercel Lambda Fallback

This project's frontend can fall back to a Vercel Serverless Function when Render is cold.

Source contract in code: `frontend/app.js`
- primary backend: `https://propozitii-nostime.onrender.com/api`
- fallback backend: `https://propozitii-nostime.vercel.app/api`
- required fallback route: `GET /api/all?minRarity=1..5&rarity=1..5`

## Required Behavior

The fallback must:
- accept `GET /api/all`
- preserve query params `minRarity` and `rarity`
- call Render upstream `/api/all`
- return JSON keys expected by frontend:
  - `haiku`, `distih`, `comparison`, `definition`, `tautogram`, `mirror`
- return CORS headers for GitHub Pages (`https://fabian20ro.github.io`)

## Minimal Function Files

Create `api/all.js`:

```js
const UPSTREAM_API_BASE = process.env.UPSTREAM_API_BASE || 'https://propozitii-nostime.onrender.com/api';
const ALLOWED_ORIGIN = process.env.ALLOWED_ORIGIN || 'https://fabian20ro.github.io';

function clampRarity(raw, fallback) {
  const parsed = Number.parseInt(raw, 10);
  if (Number.isNaN(parsed)) return fallback;
  return Math.max(1, Math.min(5, parsed));
}

function setCors(res) {
  res.setHeader('Access-Control-Allow-Origin', ALLOWED_ORIGIN);
  res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
}

module.exports = async (req, res) => {
  setCors(res);
  if (req.method === 'OPTIONS') return res.status(204).end();
  if (req.method !== 'GET') return res.status(405).json({ error: 'Method Not Allowed' });

  try {
    const minRarity = clampRarity(req.query.minRarity, 1);
    const rarity = clampRarity(req.query.rarity, 2);
    const query = new URLSearchParams({
      minRarity: String(Math.min(minRarity, rarity)),
      rarity: String(Math.max(minRarity, rarity))
    });

    const upstream = await fetch(`${UPSTREAM_API_BASE}/all?${query.toString()}`, {
      headers: { Accept: 'application/json' }
    });

    const body = await upstream.text();
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.setHeader('Cache-Control', 'no-store');
    return res.status(upstream.status).send(body);
  } catch (err) {
    return res.status(502).json({ error: 'Upstream unavailable', details: String(err) });
  }
};
```

Optional health proxy `api/health.js`:

```js
const UPSTREAM_HEALTH_URL = process.env.UPSTREAM_HEALTH_URL || 'https://propozitii-nostime.onrender.com/q/health';
const ALLOWED_ORIGIN = process.env.ALLOWED_ORIGIN || 'https://fabian20ro.github.io';

function setCors(res) {
  res.setHeader('Access-Control-Allow-Origin', ALLOWED_ORIGIN);
  res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
}

module.exports = async (req, res) => {
  setCors(res);
  if (req.method === 'OPTIONS') return res.status(204).end();
  if (req.method !== 'GET') return res.status(405).json({ error: 'Method Not Allowed' });

  try {
    const upstream = await fetch(UPSTREAM_HEALTH_URL, { headers: { Accept: 'application/json' } });
    const body = await upstream.text();
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.setHeader('Cache-Control', 'no-store');
    return res.status(upstream.status).send(body);
  } catch (err) {
    return res.status(502).json({ error: 'Health upstream unavailable', details: String(err) });
  }
};
```

Optional `vercel.json`:

```json
{
  "functions": {
    "api/*.js": {
      "maxDuration": 10
    }
  }
}
```

## First Deploy (Dashboard Flow)

1. Commit and push `api/all.js` (and optional `api/health.js`, `vercel.json`) to `master`.
2. In Vercel, click **Add New... -> Project -> Import Git Repository** and select this repo.
3. Keep:
   - Framework Preset: `Other`
   - Root Directory: repository root
   - Build Command: empty
   - Output Directory: empty
4. Add environment variables:
   - `UPSTREAM_API_BASE=https://propozitii-nostime.onrender.com/api`
   - `UPSTREAM_HEALTH_URL=https://propozitii-nostime.onrender.com/q/health`
   - `ALLOWED_ORIGIN=https://fabian20ro.github.io`
5. Deploy.
6. Verify:
   - `https://<your-vercel-domain>/api/all?minRarity=1&rarity=2`
   - `https://<your-vercel-domain>/api/health` (if implemented)
7. If you use a custom domain, update `FALLBACK_API_BASE` in `frontend/app.js` to that domain.

## Operational Notes

- Keep fallback response shape identical to Render `/api/all`.
- Do not sanitize sentence HTML in Lambda; frontend sanitizer is the single render-time guard.
- If frontend hosting changes from GitHub Pages, update `ALLOWED_ORIGIN`.
