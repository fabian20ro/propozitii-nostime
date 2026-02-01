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
const HEALTH_URL = 'https://propozitii-nostime.onrender.com/q/health';
const HEALTH_TIMEOUT = 5000;
const MAX_RETRIES = 12;
const RETRY_DELAY = 5000;

// Maps /api/all response keys to DOM element IDs
const FIELD_MAP = {
    haiku: 'haiku-text',
    couplet: 'couplet-text',
    comparison: 'comparison-text',
    definition: 'definition-text',
    tautogram: 'tautogram-text',
    mirror: 'mirror-text'
};
const FIELD_IDS = Object.values(FIELD_MAP);

// DOM elements
const refreshBtn = document.getElementById('refresh');
const errorMessage = document.getElementById('error-message');

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
 * Fetch all sentences in a single request
 * @returns {Promise<Object>} Parsed JSON with all sentence fields
 */
async function fetchAllSentences() {
    const response = await fetch(`${API_BASE}/all`);
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

    try {
        // Try fetching directly — no health check on warm backend
        const data = await fetchAllSentences();
        applySentences(data);
    } catch {
        // Fetch failed — backend likely cold-starting
        showInfo('Backend-ul pornește... Render.com Free Tier poate dura până la 60s la prima accesare.');
        const ready = await waitForBackend();
        hideMessage();

        if (!ready) {
            showError('Backend-ul nu a pornit. Încercați din nou mai târziu.');
            FIELD_IDS.forEach(id => {
                document.getElementById(id).textContent = 'Timeout';
            });
            setButtonsDisabled(false);
            return;
        }

        // Backend is up — retry once
        try {
            const data = await fetchAllSentences();
            applySentences(data);
        } catch {
            showError('Eroare la încărcarea propozițiilor. Încercați din nou.');
            FIELD_IDS.forEach(id => {
                document.getElementById(id).textContent = 'Eroare';
            });
        }
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

// Event listeners
refreshBtn.addEventListener('click', refresh);
initDexonlineDrawer();

// Initial load
refresh();
