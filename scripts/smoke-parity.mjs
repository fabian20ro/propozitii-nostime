#!/usr/bin/env node

const EXPECTED_KEYS = [
  'haiku',
  'distih',
  'comparison',
  'definition',
  'tautogram',
  'mirror'
];

const QUERY = '?minRarity=1&rarity=2';
const TIMEOUT_MS = Number.parseInt(process.env.SMOKE_TIMEOUT_MS ?? '65000', 10);
const PRIMARY_API_BASE = process.env.SMOKE_PRIMARY_API_BASE ?? 'https://propozitii-nostime.onrender.com/api';
const FALLBACK_API_BASE = process.env.SMOKE_FALLBACK_API_BASE ?? 'https://propozitii-nostime.vercel.app/api';

function buildUrl(base) {
  return new URL(`all${QUERY}`, base.endsWith('/') ? base : `${base}/`).toString();
}

async function fetchJson(label, base) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), TIMEOUT_MS);

  try {
    const response = await fetch(buildUrl(base), { signal: controller.signal });
    if (!response.ok) {
      throw new Error(`${label} returned HTTP ${response.status}`);
    }

    // Check for telemetry headers
    const serverTiming = response.headers.get('Server-Timing');
    const xResponseTime = response.headers.get('X-Response-Time-Ms');
    if (!serverTiming || !xResponseTime) {
      console.warn(`[${label}] Warning: Telemetry headers (Server-Timing or X-Response-Time-Ms) are missing.`);
    }

    const data = await response.json();
    validateShape(label, data);
    return data;
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw new Error(`${label} timed out after ${TIMEOUT_MS}ms`);
    }
    throw error;
  } finally {
    clearTimeout(timeoutId);
  }
}

function validateShape(label, data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    throw new Error(`${label} returned a non-object JSON payload`);
  }

  const keys = Object.keys(data).sort();
  const expected = [...EXPECTED_KEYS].sort();
  const missing = expected.filter(key => !(key in data));
  const extra = keys.filter(key => !EXPECTED_KEYS.includes(key));

  if (missing.length || extra.length) {
    throw new Error(
      `${label} response shape mismatch: missing=[${missing.join(', ') || 'none'}], extra=[${extra.join(', ') || 'none'}]`
    );
  }

  for (const key of EXPECTED_KEYS) {
    if (typeof data[key] !== 'string' || data[key].length === 0) {
      throw new Error(`${label} field ${key} must be a non-empty string`);
    }
  }
}

function summarize(data) {
  return EXPECTED_KEYS.map(key => `${key}=${data[key].length}`).join(', ');
}

async function main() {
  const [primary, fallback] = await Promise.all([
    fetchJson('primary', PRIMARY_API_BASE),
    fetchJson('fallback', FALLBACK_API_BASE)
  ]);

  console.log(`primary:  ${summarize(primary)}`);
  console.log(`fallback: ${summarize(fallback)}`);
  console.log('PASS: Render primary and Vercel fallback both satisfy the /api/all contract.');
}

main().catch(error => {
  console.error(`Smoke parity check failed: ${error.message}`);
  process.exit(1);
});
