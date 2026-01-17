// API base URL - update this when deploying to Render
const API_BASE = 'https://propozitii-nostime.onrender.com/api';

// DOM elements
const haikuText = document.getElementById('haiku-text');
const fivewordText = document.getElementById('fiveword-text');
const refreshBtn = document.getElementById('refresh');
const resetBtn = document.getElementById('reset');
const errorMessage = document.getElementById('error-message');

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
 * Show loading state in the sentence containers
 */
function showLoading() {
    haikuText.innerHTML = '<span class="loading">Se incarca...</span>';
    fivewordText.innerHTML = '<span class="loading">Se incarca...</span>';
}

/**
 * Show error message
 * @param {string} message - The error message to display
 */
function showError(message) {
    errorMessage.textContent = message;
    errorMessage.classList.remove('hidden');
    setTimeout(() => {
        errorMessage.classList.add('hidden');
    }, 5000);
}

/**
 * Hide error message
 */
function hideError() {
    errorMessage.classList.add('hidden');
}

/**
 * Disable buttons during loading
 * @param {boolean} disabled - Whether to disable the buttons
 */
function setButtonsDisabled(disabled) {
    refreshBtn.disabled = disabled;
    resetBtn.disabled = disabled;
}

/**
 * Refresh both sentences
 */
async function refresh() {
    hideError();
    showLoading();
    setButtonsDisabled(true);

    try {
        const [haiku, fiveword] = await Promise.all([
            fetchSentence('haiku'),
            fetchSentence('five-word')
        ]);

        haikuText.innerHTML = haiku;
        fivewordText.innerHTML = fiveword;
    } catch (error) {
        console.error('Error fetching sentences:', error);
        showError('Eroare la incarcarea propozitiilor. Backend-ul poate fi in curs de pornire (cold start). Incercati din nou in cateva secunde.');
        haikuText.innerHTML = '<span class="loading">Eroare - incercati din nou</span>';
        fivewordText.innerHTML = '<span class="loading">Eroare - incercati din nou</span>';
    } finally {
        setButtonsDisabled(false);
    }
}

/**
 * Reset the rhyme providers and refresh
 */
async function resetAndRefresh() {
    hideError();
    showLoading();
    setButtonsDisabled(true);

    try {
        await fetch(`${API_BASE}/reset`, { method: 'POST' });
        await refresh();
    } catch (error) {
        console.error('Error resetting:', error);
        showError('Eroare la resetare. Incercati din nou.');
    } finally {
        setButtonsDisabled(false);
    }
}

// Event listeners
refreshBtn.addEventListener('click', refresh);
resetBtn.addEventListener('click', resetAndRefresh);

// Initial load
refresh();
