// API configuration
const API_BASE = 'https://propozitii-nostime.onrender.com/api';
const HEALTH_URL = 'https://propozitii-nostime.onrender.com/q/health';
const HEALTH_TIMEOUT = 5000; // 5 seconds
const MAX_RETRIES = 12; // ~60 seconds total wait time
const RETRY_DELAY = 5000; // 5 seconds between retries

// DOM elements
const haikuText = document.getElementById('haiku-text');
const fivewordText = document.getElementById('fiveword-text');
const refreshBtn = document.getElementById('refresh');
const resetBtn = document.getElementById('reset');
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
        const message = `Backend-ul porneste... (${secondsWaited}s)`;
        haikuText.innerHTML = `<span class="loading">${message}</span>`;
        fivewordText.innerHTML = '<span class="loading">Render Free Tier - cold start</span>';

        await new Promise(resolve => setTimeout(resolve, RETRY_DELAY));
    }
    return false;
}

/**
 * Fetch a sentence from the API
 * @param {string} endpoint - The API endpoint (haiku or five-word)
 * @returns {Promise<string>} The sentence HTML
 */
async function fetchSentence(endpoint) {
    const response = await fetch(`${API_BASE}/${endpoint}`);
    if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
    }
    const data = await response.json();
    return data.sentence;
}

/**
 * Show loading state
 */
function showLoading() {
    haikuText.innerHTML = '<span class="loading">Se incarca...</span>';
    fivewordText.innerHTML = '<span class="loading">Se incarca...</span>';
}

/**
 * Show info message (not an error, just informational)
 * @param {string} message
 */
function showInfo(message) {
    errorMessage.textContent = message;
    errorMessage.classList.remove('hidden');
    errorMessage.style.background = '#e8f4fd';
    errorMessage.style.color = '#0066cc';
}

/**
 * Show error message
 * @param {string} message
 */
function showError(message) {
    errorMessage.textContent = message;
    errorMessage.classList.remove('hidden');
    errorMessage.style.background = '#fee';
    errorMessage.style.color = '#c00';
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
    resetBtn.disabled = disabled;
}

/**
 * Refresh both sentences
 */
async function refresh() {
    hideMessage();
    showLoading();
    setButtonsDisabled(true);

    try {
        // Quick health check first
        const isHealthy = await checkHealth();

        if (!isHealthy) {
            showInfo('Backend-ul porneste... Render Free Tier poate dura pana la 60s la prima accesare.');
            const ready = await waitForBackend();
            hideMessage();

            if (!ready) {
                showError('Backend-ul nu a pornit. Incercati din nou mai tarziu.');
                haikuText.innerHTML = '<span class="loading">Timeout</span>';
                fivewordText.innerHTML = '<span class="loading">Timeout</span>';
                setButtonsDisabled(false);
                return;
            }
        }

        const [haiku, fiveword] = await Promise.all([
            fetchSentence('haiku'),
            fetchSentence('five-word')
        ]);

        haikuText.innerHTML = haiku;
        fivewordText.innerHTML = fiveword;
    } catch (error) {
        console.error('Error fetching sentences:', error);
        showError('Eroare la incarcarea propozitiilor. Incercati din nou.');
        haikuText.innerHTML = '<span class="loading">Eroare</span>';
        fivewordText.innerHTML = '<span class="loading">Eroare</span>';
    } finally {
        setButtonsDisabled(false);
    }
}

/**
 * Reset the rhyme providers and refresh
 */
async function resetAndRefresh() {
    hideMessage();
    showLoading();
    setButtonsDisabled(true);

    try {
        await fetch(`${API_BASE}/reset`, { method: 'POST' });
        await refresh();
    } catch (error) {
        console.error('Error resetting:', error);
        showError('Eroare la resetare. Incercati din nou.');
        setButtonsDisabled(false);
    }
}

// Event listeners
refreshBtn.addEventListener('click', refresh);
resetBtn.addEventListener('click', resetAndRefresh);

// Initial load
refresh();
