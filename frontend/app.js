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
                    } else if (attr.name === 'href' && attr.value.trimStart().startsWith('javascript:')) {
                        child.removeAttribute(attr.name);
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
const HEALTH_TIMEOUT = 5000; // 5 seconds
const MAX_RETRIES = 12; // ~60 seconds total wait time
const RETRY_DELAY = 5000; // 5 seconds between retries

// Endpoint configuration
const ENDPOINTS = [
    { id: 'haiku-text', endpoint: 'haiku' },
    { id: 'couplet-text', endpoint: 'couplet' },
    { id: 'comparison-text', endpoint: 'comparison' },
    { id: 'definition-text', endpoint: 'definition' },
    { id: 'tautogram-text', endpoint: 'tautogram' },
    { id: 'mirror-text', endpoint: 'mirror' }
];

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
    } catch (error) {
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

        ENDPOINTS.forEach((e, idx) => {
            const el = document.getElementById(e.id);
            if (idx === 0) {
                el.innerHTML = `<span class="loading">${message}</span>`;
            } else {
                el.innerHTML = '<span class="loading">Render Free Tier – pornire la rece</span>';
            }
        });

        await new Promise(resolve => setTimeout(resolve, RETRY_DELAY));
    }
    return false;
}

/**
 * Fetch a sentence from the API
 * @param {string} endpoint - The API endpoint
 * @returns {Promise<string>} The sentence HTML
 */
async function fetchSentence(endpoint) {
    const response = await fetch(`${API_BASE}/${endpoint}`);
    if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
    }
    const data = await response.json();
    if (!data || typeof data.sentence !== 'string') {
        throw new Error(`Invalid response for ${endpoint}: missing sentence`);
    }
    return sanitizeHtml(data.sentence);
}

/**
 * Show loading state
 */
function showLoading() {
    ENDPOINTS.forEach(e => {
        document.getElementById(e.id).innerHTML = '<span class="loading">Se încarcă...</span>';
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
 * Refresh all sentences
 */
async function refresh() {
    hideMessage();
    showLoading();
    setButtonsDisabled(true);

    try {
        // Quick health check first
        const isHealthy = await checkHealth();

        if (!isHealthy) {
            showInfo('Backend-ul pornește... Render Free Tier poate dura până la 60s la prima accesare.');
            const ready = await waitForBackend();
            hideMessage();

            if (!ready) {
                showError('Backend-ul nu a pornit. Încercați din nou mai târziu.');
                ENDPOINTS.forEach(e => {
                    document.getElementById(e.id).innerHTML = '<span class="loading">Timeout</span>';
                });
                setButtonsDisabled(false);
                return;
            }
        }

        const results = await Promise.all(
            ENDPOINTS.map(e => fetchSentence(e.endpoint))
        );

        ENDPOINTS.forEach((e, i) => {
            document.getElementById(e.id).innerHTML = results[i];
        });
    } catch (error) {
        console.error('Error fetching sentences:', error);
        showError('Eroare la încărcarea propozițiilor. Încercați din nou.');
        ENDPOINTS.forEach(e => {
            document.getElementById(e.id).innerHTML = '<span class="loading">Eroare</span>';
        });
    } finally {
        setButtonsDisabled(false);
    }
}

// Dexonline bottom drawer
function initDexonlineDrawer() {
    const drawer = document.getElementById('dex-drawer');
    const iframe = document.getElementById('dex-drawer-iframe');
    const titleEl = drawer.querySelector('.dex-drawer-title');
    const closeBtn = drawer.querySelector('.dex-drawer-close');
    const backdrop = drawer.querySelector('.dex-drawer-backdrop');

    let activeWord = null;
    let triggerLink = null;

    function openDrawer(word, url, link) {
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

        if (e.target.closest('.dex-drawer-backdrop')) {
            closeDrawer();
            return;
        }

        if (e.target.closest('.dex-drawer-panel')) {
            return;
        }

        const link = e.target.closest('.sentence a[data-word]');

        if (!link) {
            if (activeWord) closeDrawer();
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
}

// Event listeners
refreshBtn.addEventListener('click', refresh);
initDexonlineDrawer();

// Initial load
refresh();
