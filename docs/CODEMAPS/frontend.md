# Frontend Codemap

Freshness: 2026-02-13

## File Map

- Markup: `frontend/index.html`
- Logic: `frontend/app.js`
- Styles: `frontend/style.css`

## UI Structure

`index.html` renders:
- theme toggle button
- dual-range rarity slider (min + max, `1..5`, persisted in localStorage as `rarity-min`/`rarity-max`)
- 6 sentence cards (`haiku`, `distih`, `mirror`, `comparison`, `definition`, `tautogram`), each with a copy-to-clipboard button and an explain-with-AI button
- refresh button
- status/error message area
- dexonline drawer (bottom sheet with iframe)

Each card uses a `.card-header` wrapper (flex row) containing the `<h2>` title, a `.copy-btn` button, and a `.explain-btn` button. Both buttons use `data-target` pointing to the sentence div ID.

## Data Flow

1. `refresh()` is called on initial page load and refresh-button clicks.
2. `fetchAllSentences(range)` implements dual-backend strategy:
   - If `renderIsHealthy` flag is set, go directly to Render (8s timeout).
   - Otherwise try Render with 8s timeout; on success, set `renderIsHealthy = true`.
   - On Render failure: fall back to Vercel (`FALLBACK_API_BASE`) and fire `wakeRenderInBackground()`.
3. `wakeRenderInBackground()` polls Render health up to 12 times (5s apart). Once healthy, sets `renderIsHealthy` so next request bypasses fallback.
4. Both backends use the shared `fetchFrom(baseUrl, range, timeout)` helper which validates the 6 expected keys.
5. Success path: `applySentences(data)` sanitizes each field and writes to DOM.

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
- `API_BASE = https://propozitii-nostime.onrender.com/api` (Render primary)
- `FALLBACK_API_BASE = /api` (Vercel serverless, relative path)
- `HEALTH_URL = https://propozitii-nostime.onrender.com/q/health`

Rarity UI/storage contract:
- min slider id: `rarity-min`
- max slider id: `rarity-max`
- value label id: `rarity-value`
- localStorage keys: `rarity-min`, `rarity-max` (migrated from legacy `rarity-level` on first load)
- normalization/clamp: `1..5`, defaults min=`1`, max=`2`
- track highlight: `.dual-range-track` div with dynamic `linear-gradient` background

Response field mapping:
- `FIELD_MAP` expects keys: `haiku`, `distih`, `comparison`, `definition`, `tautogram`, `mirror`

If backend adds/removes sentence types, update both:
- `FIELD_MAP` in `app.js`
- corresponding card markup in `index.html`

## Copy-to-Clipboard

`extractPlainText(el)` recursively traverses DOM: text nodes → concatenate, `<BR>` → `\n`, elements → recurse children. Handles verse `<br/>` tags and `<a>` link wrappers to produce clean plain text.

`copyCardText(button)` reads `data-target`, extracts text, guards against loading/error states, calls `navigator.clipboard.writeText()`.

`showCopyFeedback(button)` swaps clipboard icon → checkmark via `.copied` CSS class for 1.5s.

## Explain-with-AI

`explainWithAI(button)` reads `data-target`, extracts sentence text via `extractPlainText()`, guards against loading/error states, builds a Google search URL prefixed with "explica semnificatia urmatoarei afirmatii: ", and opens in a new tab.

Event delegation: single listener on `.grid` container handles all 6 copy buttons and all 6 explain buttons.

Disabled state: `setButtonsDisabled()` sets `disabled` on copy buttons, explain buttons, and both rarity sliders during loading; CSS renders disabled buttons invisible (`opacity: 0; pointer-events: none`).

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

1. Add card section in `index.html` with `.card-header` wrapper, `<h2>`, `.copy-btn`, and `.explain-btn` (both with `data-target` matching the sentence div ID).
2. Add new field in `FIELD_MAP`.
3. Ensure backend `/api/all` includes matching key.

### Change backend anchor attributes

1. Update backend `DexonlineLinkAdder`.
2. Update frontend `sanitizeHtml` allowlist to keep required attrs.
3. Verify no unsafe URL path is introduced.

### Improve cold-start UX

The primary cold-start mitigation is now the Vercel fallback (users see instant results while Render wakes). For further tuning:
- `FETCH_TIMEOUT` (8s): how long to wait for Render before falling back
- `MAX_RETRIES`, `RETRY_DELAY`: background health poll cadence
- `HEALTH_TIMEOUT`: per-poll timeout
- `renderIsHealthy` flag: sticky — once Render responds, subsequent requests skip fallback

## High-Risk Areas

- Writing unsanitized backend HTML directly to `innerHTML`.
- Broadening sanitizer URL policy beyond trusted dexonline domain.
- Breaking `FIELD_MAP` alignment with backend `/api/all` response.
- Vercel fallback returning different keys/HTML than Render — both backends must stay in sync.
