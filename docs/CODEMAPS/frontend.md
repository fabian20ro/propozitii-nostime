# Frontend Codemap

Freshness: 2026-02-08

## File Map

- Markup: `frontend/index.html`
- Logic: `frontend/app.js`
- Styles: `frontend/style.css`

## UI Structure

`index.html` renders:
- theme toggle button
- rarity slider (`1..5`, persisted in localStorage)
- 6 sentence cards (`haiku`, `couplet`, `comparison`, `definition`, `tautogram`, `mirror`)
- refresh button
- status/error message area
- dexonline drawer (bottom sheet with iframe)

## Data Flow

1. `refresh()` is called on initial page load and refresh-button clicks.
2. Main request: `fetchAllSentences()` -> `GET /api/all`.
   - includes `?rarity=<1..5>`
3. On fetch failure: show info state, poll `/q/health` for up to 60s (`waitForBackend()`), retry once.
4. Success path: `applySentences(data)` sanitizes each field and writes to DOM.

## Security/Sanitization Contract

Function: `sanitizeHtml(html)`

Allowed tags: `A`, `DIV`, `BR`, `SPAN`
Allowed attributes: `href`, `target`, `rel`, `class`, `data-word`

`href` is validated using `URL` parsing and must be:
- protocol `https:`
- hostname `dexonline.ro`

Any non-allowed tags/attrs are removed before rendering.

## Backend Coupling Points

Hardcoded API endpoints:
- `API_BASE = https://propozitii-nostime.onrender.com/api`
- `HEALTH_URL = https://propozitii-nostime.onrender.com/q/health`

Rarity UI/storage contract:
- slider id: `rarity-slider`
- value label id: `rarity-value`
- localStorage key: `rarity-level`
- normalization/clamp: `1..5`, default `2`

Response field mapping:
- `FIELD_MAP` expects keys: `haiku`, `couplet`, `comparison`, `definition`, `tautogram`, `mirror`

If backend adds/removes sentence types, update both:
- `FIELD_MAP` in `app.js`
- corresponding card markup in `index.html`

## Dexonline Drawer Behavior

`initDexonlineDrawer()` features:
- first click on a linked word opens drawer with iframe definition
- second click on same word opens dexonline in new tab and closes drawer
- drag header to resize drawer height (20vh..90vh)
- store height in localStorage (`dex-drawer-height`)
- close via X button or Escape

## Styling Notes

- Theme variables live under `:root` and `[data-theme="dark"]`.
- Layout uses responsive CSS grid (1/2/3 columns at breakpoints).
- Drawer is a fixed bottom sheet; visible state toggled by `.dex-drawer-hidden`.

## Common Frontend Changes

### Add new sentence card

1. Add card section in `index.html`.
2. Add new field in `FIELD_MAP`.
3. Ensure backend `/api/all` includes matching key.

### Change backend anchor attributes

1. Update backend `DexonlineLinkAdder`.
2. Update frontend `sanitizeHtml` allowlist to keep required attrs.
3. Verify no unsafe URL path is introduced.

### Improve cold-start UX

Update:
- retry limits/timeouts (`MAX_RETRIES`, `RETRY_DELAY`, `HEALTH_TIMEOUT`)
- user-facing messages in `refresh()` / `waitForBackend()`

## High-Risk Areas

- Writing unsanitized backend HTML directly to `innerHTML`.
- Broadening sanitizer URL policy beyond trusted dexonline domain.
- Breaking `FIELD_MAP` alignment with backend `/api/all` response.
