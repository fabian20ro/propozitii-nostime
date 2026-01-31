# Frontend Codemap

> Freshness: 2026-01-31 | 3 files, ~808 lines

## Files

- `frontend/index.html` (113 lines) -- Single-page app with 6 card sections + dexonline drawer
- `frontend/app.js` (321 lines) -- API calls, cold-start handling, sanitization, theme, drawer
- `frontend/style.css` (374 lines) -- Responsive grid, dark mode, bottom drawer styles

## HTML Structure

```
<body>
  <button>   Dark mode toggle (fixed top-right)
  <main>     6x <section class="card"> (haiku, couplet, comparison, definition, tautogram, mirror)
  <footer>   GitHub badge + credits
  <div#dex-drawer>  Bottom sheet drawer (backdrop + panel with iframe)
```

CSP: scripts `'self'`, frames `https://dexonline.ro`, connect `https://propozitii-nostime.onrender.com`

## app.js Key Functions

| Function | Purpose |
|----------|---------|
| `initTheme()` / `toggleTheme()` | Dark mode via `data-theme` + localStorage |
| `sanitizeHtml(html)` | Allowlist sanitizer: `<a>`, `<div>`, `<br>`, `<span>` |
| `checkHealth()` | Pings `/q/health` with 5s timeout |
| `waitForBackend()` | Polls health 12x (60s total) for Render cold starts |
| `fetchSentence(endpoint)` | GET + validate + sanitize |
| `refresh()` | Health check -> wait if needed -> parallel fetch all 6 -> update DOM |
| `initDexonlineDrawer()` | Bottom sheet drawer for word definitions (see below) |

## Dexonline Drawer

Click-based bottom sheet replacing the old hover popup. Unified behavior for desktop and mobile:

- **First click** on a word → opens a 50vh drawer with dexonline.ro iframe
- **Second click** on the same word → navigates to dexonline.ro in a new tab
- **Click different word** → switches drawer content
- **Close**: backdrop click, × button, or Escape key
- Focus management: moves focus to close button on open, restores to trigger link on close

## Config

```js
API_BASE = 'https://propozitii-nostime.onrender.com/api'
HEALTH_URL = 'https://propozitii-nostime.onrender.com/q/health'
```
