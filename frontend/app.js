// Theme handling
const themeToggle = document.getElementById('theme-toggle');
const THEME_KEY = 'theme';

function initTheme() {
    const savedTheme = localStorage.getItem(THEME_KEY);
    if (savedTheme) {
        document.documentElement.setAttribute('data-theme', savedTheme);
    } else if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
        document.documentElement.setAttribute('data-theme', 'dark');
    }
}

function toggleTheme() {
    const currentTheme = document.documentElement.getAttribute('data-theme');
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';

    if (newTheme === 'light') {
        document.documentElement.removeAttribute('data-theme');
    } else {
        document.documentElement.setAttribute('data-theme', newTheme);
    }
    localStorage.setItem(THEME_KEY, newTheme);
}

// Initialize theme immediately
initTheme();
themeToggle.addEventListener('click', toggleTheme);

/**
 * Sanitize HTML to only allow safe tags used by the backend.
 * Strips everything except <a>, <div>, <br>, and <span> with safe attributes.
 * @param {string} html
 * @returns {string}
 */
function sanitizeHtml(html) {
    const div = document.createElement('div');
    div.innerHTML = html;

    function cleanNode(node) {
        const allowed = ['A', 'DIV', 'BR', 'SPAN'];
        const safeAttrs = ['href', 'target', 'rel', 'class', 'data-word'];
        const children = Array.from(node.childNodes);
        for (const child of children) {
            if (child.nodeType === Node.ELEMENT_NODE) {
                if (!allowed.includes(child.tagName)) {
                    child.remove();
                    continue;
                }
                for (const attr of Array.from(child.attributes)) {
                    if (!safeAttrs.includes(attr.name)) {
                        child.removeAttribute(attr.name);
                    } else if (attr.name === 'href') {
                        try {
                            const parsed = new URL(attr.value, window.location.href);
                            if (parsed.protocol !== 'https:' || parsed.hostname !== 'dexonline.ro') {
                                child.removeAttribute(attr.name);
                            }
                        } catch {
                            child.removeAttribute(attr.name);
                        }
                    }
                }
                cleanNode(child);
            }
        }
    }

    cleanNode(div);
    return div.innerHTML;
}

// API configuration
const API_BASE = 'https://propozitii-nostime.onrender.com/api';
const FALLBACK_API_BASE = 'https://propozitii-nostime.vercel.app/api';
const HEALTH_URL = 'https://propozitii-nostime.onrender.com/q/health';
const HEALTH_TIMEOUT = 5000;
const FETCH_TIMEOUT = 8000;
const MAX_RETRIES = 12;
const RETRY_DELAY = 5000;

// Track whether Render backend is known to be up
let renderIsHealthy = false;
const RARITY_MIN_KEY = 'rarity-min';
const RARITY_MAX_KEY = 'rarity-max';
const OLD_RARITY_KEY = 'rarity-level';
const DEFAULT_RARITY_MIN = 1;
const DEFAULT_RARITY_MAX = 2;
const RARITY_FLOOR = 1;
const RARITY_CEIL = 5;

// Maps /api/all response keys to DOM element IDs
const FIELD_MAP = {
    haiku: 'haiku-text',
    distih: 'distih-text',
    comparison: 'comparison-text',
    definition: 'definition-text',
    tautogram: 'tautogram-text',
    mirror: 'mirror-text'
};
const FIELD_IDS = Object.values(FIELD_MAP);

// DOM elements
const refreshBtn = document.getElementById('refresh');
const errorMessage = document.getElementById('error-message');
const rarityMinSlider = document.getElementById('rarity-min');
const rarityMaxSlider = document.getElementById('rarity-max');
const rarityValue = document.getElementById('rarity-value');
const dualRangeTrack = document.querySelector('.dual-range-track');

function clampRarity(value, fallback) {
    const parsed = Number.parseInt(value, 10);
    if (Number.isNaN(parsed)) return fallback;
    return Math.max(RARITY_FLOOR, Math.min(RARITY_CEIL, parsed));
}

function rarityLabel(level) {
    switch (level) {
        case 1: return 'Foarte comun';
        case 2: return 'Uzual extins';
        case 3: return 'Mai puțin uzual';
        case 4: return 'Rar / specializat';
        case 5: return 'Foarte rar';
        default: return 'Uzual extins';
    }
}

function updateRangeTrack() {
    const min = Number(rarityMinSlider.value);
    const max = Number(rarityMaxSlider.value);
    const pctMin = ((min - RARITY_FLOOR) / (RARITY_CEIL - RARITY_FLOOR)) * 100;
    const pctMax = ((max - RARITY_FLOOR) / (RARITY_CEIL - RARITY_FLOOR)) * 100;
    dualRangeTrack.style.background =
        `linear-gradient(to right, var(--border-color) ${pctMin}%, var(--primary-color) ${pctMin}%, var(--primary-color) ${pctMax}%, var(--border-color) ${pctMax}%)`;
}

function setRarityRange(min, max) {
    const normMin = clampRarity(min, DEFAULT_RARITY_MIN);
    const normMax = clampRarity(max, DEFAULT_RARITY_MAX);
    const lo = Math.min(normMin, normMax);
    const hi = Math.max(normMin, normMax);
    rarityMinSlider.value = String(lo);
    rarityMaxSlider.value = String(hi);
    if (lo === hi) {
        rarityValue.textContent = `${lo} (${rarityLabel(lo)})`;
    } else {
        rarityValue.textContent = `${lo} (${rarityLabel(lo)}) – ${hi} (${rarityLabel(hi)})`;
    }
    localStorage.setItem(RARITY_MIN_KEY, String(lo));
    localStorage.setItem(RARITY_MAX_KEY, String(hi));
    updateRangeTrack();
}

function getCurrentRarityRange() {
    return {
        min: clampRarity(rarityMinSlider.value, DEFAULT_RARITY_MIN),
        max: clampRarity(rarityMaxSlider.value, DEFAULT_RARITY_MAX)
    };
}

function initRarity() {
    // Migrate from old single-slider key
    const oldStored = localStorage.getItem(OLD_RARITY_KEY);
    if (oldStored && !localStorage.getItem(RARITY_MAX_KEY)) {
        const val = clampRarity(oldStored, DEFAULT_RARITY_MAX);
        setRarityRange(DEFAULT_RARITY_MIN, val);
        localStorage.removeItem(OLD_RARITY_KEY);
        return;
    }
    const storedMin = localStorage.getItem(RARITY_MIN_KEY);
    const storedMax = localStorage.getItem(RARITY_MAX_KEY);
    setRarityRange(
        storedMin ?? DEFAULT_RARITY_MIN,
        storedMax ?? DEFAULT_RARITY_MAX
    );
}

/**
 * Check if backend is healthy
 * @returns {Promise<boolean>}
 */
async function checkHealth() {
    try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), HEALTH_TIMEOUT);

        const response = await fetch(HEALTH_URL, { signal: controller.signal });
        clearTimeout(timeoutId);

        if (response.ok) {
            const data = await response.json();
            return data.status === 'UP';
        }
        return false;
    } catch {
        return false;
    }
}

/**
 * Wait for backend to be ready with visual feedback
 * @returns {Promise<boolean>} true if backend became ready, false if timed out
 */
async function waitForBackend() {
    for (let i = 0; i < MAX_RETRIES; i++) {
        const isHealthy = await checkHealth();
        if (isHealthy) {
            return true;
        }

        const secondsWaited = (i + 1) * (RETRY_DELAY / 1000);
        const message = `Backend-ul pornește... (${secondsWaited}s)`;

        FIELD_IDS.forEach((id, idx) => {
            const el = document.getElementById(id);
            if (idx === 0) {
                el.textContent = message;
            } else {
                el.textContent = 'Render.com Free Tier – pornire la rece';
            }
        });

        await new Promise(resolve => setTimeout(resolve, RETRY_DELAY));
    }
    return false;
}

/**
 * Fetch all sentences from a given API base URL with a timeout
 * @param {string} baseUrl
 * @param {{ min: number, max: number }} range
 * @param {number} timeout
 * @returns {Promise<Object>} Parsed JSON with all sentence fields
 */
async function fetchFrom(baseUrl, { min, max }, timeout) {
    const query = new URLSearchParams({
        minRarity: String(clampRarity(min, DEFAULT_RARITY_MIN)),
        rarity: String(clampRarity(max, DEFAULT_RARITY_MAX))
    });
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);
    const response = await fetch(`${baseUrl}/all?${query.toString()}`, { signal: controller.signal });
    clearTimeout(timeoutId);
    if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
    }
    const data = await response.json();
    for (const key of Object.keys(FIELD_MAP)) {
        if (!data[key] || typeof data[key] !== 'string') {
            throw new Error(`Invalid response: missing ${key}`);
        }
    }
    return data;
}

/**
 * Fetch all sentences — tries Render first; on failure, falls back to Vercel serverless
 * and wakes Render in the background.
 * @returns {Promise<Object>} Parsed JSON with all sentence fields
 */
async function fetchAllSentences(range) {
    // If Render is known healthy, go straight to it
    if (renderIsHealthy) {
        try {
            return await fetchFrom(API_BASE, range, FETCH_TIMEOUT);
        } catch {
            renderIsHealthy = false;
            // fall through to fallback
        }
    }

    // Try Render with a short timeout — if it responds quickly, great
    try {
        const data = await fetchFrom(API_BASE, range, FETCH_TIMEOUT);
        renderIsHealthy = true;
        return data;
    } catch {
        // Render is cold — use Vercel fallback and wake Render in background
    }

    // Wake Render in background (fire-and-forget health poll)
    wakeRenderInBackground();

    // Serve from Vercel fallback
    return await fetchFrom(FALLBACK_API_BASE, range, FETCH_TIMEOUT);
}

/**
 * Polls Render health in background so it's warm for next request.
 */
function wakeRenderInBackground() {
    if (wakeRenderInBackground._running) return;
    wakeRenderInBackground._running = true;
    (async () => {
        for (let i = 0; i < MAX_RETRIES; i++) {
            const up = await checkHealth();
            if (up) { renderIsHealthy = true; break; }
            await new Promise(r => setTimeout(r, RETRY_DELAY));
        }
        wakeRenderInBackground._running = false;
    })();
}
wakeRenderInBackground._running = false;

/**
 * Show loading state
 */
function showLoading() {
    FIELD_IDS.forEach(id => {
        document.getElementById(id).textContent = 'Se încarcă...';
    });
}

/**
 * Show info message (not an error, just informational)
 * @param {string} message
 */
function showInfo(message) {
    errorMessage.textContent = message;
    errorMessage.classList.remove('hidden', 'error');
    errorMessage.classList.add('info');
}

/**
 * Show error message
 * @param {string} message
 */
function showError(message) {
    errorMessage.textContent = message;
    errorMessage.classList.remove('hidden', 'info');
    errorMessage.classList.add('error');
    setTimeout(() => errorMessage.classList.add('hidden'), 5000);
}

/**
 * Hide message
 */
function hideMessage() {
    errorMessage.classList.add('hidden');
}

/**
 * Disable buttons during loading
 * @param {boolean} disabled
 */
function setButtonsDisabled(disabled) {
    refreshBtn.disabled = disabled;
    rarityMinSlider.disabled = disabled;
    rarityMaxSlider.disabled = disabled;
    document.querySelectorAll('.copy-btn, .explain-btn').forEach(btn => {
        btn.disabled = disabled;
    });
}

/**
 * Apply fetched sentences to the DOM (sanitized HTML from backend)
 * @param {Object} data - Response from /api/all
 */
function applySentences(data) {
    for (const [key, id] of Object.entries(FIELD_MAP)) {
        const el = document.getElementById(id);
        // Backend returns HTML with <a> tags for dexonline links — sanitize before inserting
        const safe = sanitizeHtml(data[key]);
        el.innerHTML = safe;
    }
}

/**
 * Refresh all sentences
 */
async function refresh() {
    hideMessage();
    showLoading();
    setButtonsDisabled(true);
    const range = getCurrentRarityRange();

    try {
        const data = await fetchAllSentences(range);
        applySentences(data);
    } catch {
        showError('Eroare la încărcarea propozițiilor. Încercați din nou.');
        FIELD_IDS.forEach(id => {
            document.getElementById(id).textContent = 'Eroare';
        });
    } finally {
        setButtonsDisabled(false);
    }
}

// Dexonline bottom drawer
function initDexonlineDrawer() {
    const DRAWER_HEIGHT_KEY = 'dex-drawer-height';
    const MIN_HEIGHT_VH = 20;
    const MAX_HEIGHT_VH = 90;

    const drawer = document.getElementById('dex-drawer');
    const panel = drawer.querySelector('.dex-drawer-panel');
    const header = drawer.querySelector('.dex-drawer-header');
    const iframe = document.getElementById('dex-drawer-iframe');
    const titleEl = drawer.querySelector('.dex-drawer-title');
    const closeBtn = drawer.querySelector('.dex-drawer-close');

    let activeWord = null;
    let triggerLink = null;

    function applyDrawerHeight() {
        const saved = localStorage.getItem(DRAWER_HEIGHT_KEY);
        if (saved) {
            panel.style.setProperty('--drawer-height', saved + 'px');
        }
    }

    function clampHeight(px) {
        const minPx = window.innerHeight * (MIN_HEIGHT_VH / 100);
        const maxPx = window.innerHeight * (MAX_HEIGHT_VH / 100);
        return Math.max(minPx, Math.min(maxPx, px));
    }

    // Drag-to-resize
    function initDrag() {
        let dragging = false;

        header.addEventListener('pointerdown', function (e) {
            if (e.target.closest('.dex-drawer-close')) return;
            dragging = true;
            header.setPointerCapture(e.pointerId);
            panel.style.transition = 'none';
            iframe.style.pointerEvents = 'none';
        });

        header.addEventListener('pointermove', function (e) {
            if (!dragging) return;
            const newHeight = clampHeight(window.innerHeight - e.clientY);
            panel.style.setProperty('--drawer-height', newHeight + 'px');
        });

        header.addEventListener('pointerup', function (e) {
            if (!dragging) return;
            dragging = false;
            panel.style.transition = '';
            iframe.style.pointerEvents = '';
            const current = panel.getBoundingClientRect().height;
            localStorage.setItem(DRAWER_HEIGHT_KEY, Math.round(current));
        });
    }

    function openDrawer(word, url, link) {
        applyDrawerHeight();
        titleEl.textContent = decodeURIComponent(word);
        iframe.src = url;
        drawer.classList.remove('dex-drawer-hidden');
        drawer.setAttribute('aria-hidden', 'false');
        activeWord = word;
        triggerLink = link;
        closeBtn.focus();
    }

    function closeDrawer() {
        drawer.classList.add('dex-drawer-hidden');
        drawer.setAttribute('aria-hidden', 'true');
        iframe.src = '';
        activeWord = null;
        if (triggerLink) {
            triggerLink.focus();
            triggerLink = null;
        }
    }

    document.addEventListener('click', function (e) {
        if (e.target.closest('.dex-drawer-close')) {
            closeDrawer();
            return;
        }

        if (e.target.closest('.dex-drawer-panel')) {
            return;
        }

        const link = e.target.closest('.sentence a[data-word]');

        if (!link) {
            return;
        }

        e.preventDefault();

        const word = link.dataset.word;
        const url = link.href;

        if (activeWord === word) {
            window.open(url, '_blank', 'noopener');
            closeDrawer();
        } else {
            openDrawer(word, url, link);
        }
    });

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && activeWord) closeDrawer();
    });

    initDrag();
}

// Copy to clipboard
function extractPlainText(el) {
    let text = '';
    for (const node of el.childNodes) {
        if (node.nodeType === Node.TEXT_NODE) {
            text += node.textContent;
        } else if (node.nodeType === Node.ELEMENT_NODE) {
            if (node.tagName === 'BR') {
                text += '\n';
            } else {
                text += extractPlainText(node);
            }
        }
    }
    return text;
}

function showCopyFeedback(button) {
    button.classList.add('copied');
    setTimeout(() => button.classList.remove('copied'), 1500);
}

const PLACEHOLDER_TEXTS = new Set(['Se încarcă...', 'Eroare', 'Timeout']);

function getCardText(button) {
    const el = document.getElementById(button.dataset.target);
    if (!el) return null;
    const text = extractPlainText(el).trim();
    if (!text || PLACEHOLDER_TEXTS.has(text)) return null;
    return text;
}

async function copyCardText(button) {
    const text = getCardText(button);
    if (!text) return;

    try {
        await navigator.clipboard.writeText(text);
        showCopyFeedback(button);
    } catch {
        // Clipboard permission denied - silent fail
    }
}

function explainWithAI(button) {
    const text = getCardText(button);
    if (!text) return;

    const query = 'explica semnificatia urmatoarei afirmatii: ' + text;
    const url = 'https://www.google.com/search?udm=50&q=' + encodeURIComponent(query);
    window.open(url, '_blank', 'noopener');
}

document.querySelector('.grid').addEventListener('click', function (e) {
    const copyBtn = e.target.closest('.copy-btn');
    if (copyBtn) { copyCardText(copyBtn); return; }

    const explainBtn = e.target.closest('.explain-btn');
    if (explainBtn) explainWithAI(explainBtn);
});

// Rarity slider event listeners
function onRarityInput() {
    let min = Number(rarityMinSlider.value);
    let max = Number(rarityMaxSlider.value);
    if (min > max) {
        [min, max] = [max, min];
        rarityMinSlider.value = String(min);
        rarityMaxSlider.value = String(max);
    }
    setRarityRange(min, max);
}

refreshBtn.addEventListener('click', refresh);
rarityMinSlider.addEventListener('input', onRarityInput);
rarityMaxSlider.addEventListener('input', onRarityInput);
rarityMinSlider.addEventListener('change', refresh);
rarityMaxSlider.addEventListener('change', refresh);
initDexonlineDrawer();
initRarity();

// Initial load
refresh();
