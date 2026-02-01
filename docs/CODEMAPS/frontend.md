# Frontend Codemap

> Freshness: 2026-02-01 | 3 files, ~845 lines

## Files

- `frontend/index.html` (113 lines) -- Single-page app with 6 card sections + draggable dexonline drawer
- `frontend/app.js` (372 lines) -- API calls, cold-start handling, sanitization, theme, draggable drawer
- `frontend/style.css` (360 lines) -- Responsive grid, dark mode, draggable bottom drawer styles

## HTML Structure

```
<body>
  <button>   Dark mode toggle (fixed top-right)
  <main>     6x <section class="card"> (haiku, couplet, comparison, definition, tautogram, mirror)
  <footer>   GitHub badge + tech stack credits
  <div#dex-drawer>  Draggable bottom sheet drawer (panel with drag handle + header + iframe)
```

CSP: `script-src 'self'`; `frame-src https://dexonline.ro`; `connect-src https://propozitii-nostime.onrender.com`; `object-src 'none'`; `base-uri 'self'`; `form-action 'none'`
Iframe: `sandbox="allow-scripts allow-same-origin"`

## app.js Key Functions

| Function | Purpose |
|----------|---------|
| `initTheme()` / `toggleTheme()` | Dark mode via `data-theme` + localStorage |
| `sanitizeHtml(html)` | Allowlist sanitizer: `<a>`, `<div>`, `<br>`, `<span>`; href validated via URL parsing (only `https://dexonline.ro` allowed) |
| `checkHealth()` | Pings `/q/health` with 5s timeout |
| `waitForBackend()` | Polls health 12x (60s total) for Render.com cold starts |
| `fetchSentence(endpoint)` | GET + validate + sanitize |
| `fetchAllSentences()` | Single `GET /api/all` request returning all 6 sentences |
| `applySentences(data)` | Sanitize + render all sentences into DOM |
| `refresh()` | Fetch via `/api/all` -> on failure, wait for backend cold start -> retry once |
| `initDexonlineDrawer()` | Draggable bottom sheet drawer for word definitions (see below) |

## Dexonline Drawer

Click-based, draggable bottom sheet replacing the old hover popup. Unified behavior for desktop and mobile:

- **First click** on a word → opens drawer with dexonline.ro iframe
- **Second click** on the same word → navigates to dexonline.ro in a new tab
- **Click different word** → switches drawer content
- **Close**: click outside panel, x button, or Escape key
- **Drag to resize**: drag the header bar up/down to adjust height (pointer events with `setPointerCapture` for mouse+touch)
- **Persistent height**: drawer height saved to localStorage (`dex-drawer-height`) and restored on next open
- **Height limits**: clamped between 20vh and 90vh; defaults to 50vh on first use
- **Visual drag handle**: small horizontal bar (`span.dex-drawer-handle`) at the top of the panel as affordance
- **Iframe scroll offset**: `margin-top: -300px` on the iframe to skip dexonline.ro site header/navigation and show definitions directly
- Focus management: moves focus to close button on open, restores to trigger link on close

## Config

```js
API_BASE = 'https://propozitii-nostime.onrender.com/api'
HEALTH_URL = 'https://propozitii-nostime.onrender.com/q/health'
```
