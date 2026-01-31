# Frontend Codemap

> Freshness: 2026-01-31 | 2 files, ~352 lines

## Files

- `frontend/index.html` (97 lines) -- Single-page app with 6 card sections
- `frontend/app.js` (255 lines) -- API calls, cold-start handling, sanitization, theme

## HTML Structure

```
<body>
  <header>  Title + dark mode toggle
  <main>    6x <section class="card"> (haiku, couplet, comparison, definition, tautogram, mirror)
  <footer>  Refresh button + error message area
```

CSP: scripts `'self'`, frames `https://dexonline.ro`, connect `https://propozitii-nostime.onrender.com`

## app.js Key Functions

| Function | Purpose |
|----------|---------|
| `initTheme()` / `toggleTheme()` | Dark mode via `data-theme` + localStorage |
| `sanitizeHtml(html)` | Allowlist sanitizer: `<a>`, `<div>`, `<iframe>`, `<br>`, `<span>` |
| `checkHealth()` | Pings `/q/health` with 5s timeout |
| `waitForBackend()` | Polls health 12x (60s total) for Render cold starts |
| `fetchSentence(endpoint)` | GET + validate + sanitize |
| `refresh()` | Health check -> wait if needed -> parallel fetch all 6 -> update DOM |

## Config

```js
API_BASE = 'https://propozitii-nostime.onrender.com/api'
HEALTH_URL = 'https://propozitii-nostime.onrender.com/q/health'
```
