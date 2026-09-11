/**
 * health.js
 * Utility functions to ping the backend and gateway health endpoints.
 */

const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';
const GATEWAY_URL = import.meta.env.VITE_GATEWAY_URL || 'http://localhost:3001';

/**
 * Check the Spring Boot backend health.
 * @returns {Promise<{ok: boolean, data: object|null, error: string|null}>}
 */
export async function checkBackendHealth() {
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);
    const res = await fetch(`${BACKEND_URL}/api/health`, {
      signal: controller.signal,
    });
    clearTimeout(timeout);
    const data = await res.json();
    return { ok: res.ok, data, error: null };
  } catch (err) {
    return {
      ok: false,
      data: null,
      error: err.name === 'AbortError' ? 'Timeout (5s)' : err.message,
    };
  }
}

/**
 * Check the Node.js gateway health.
 * @returns {Promise<{ok: boolean, data: object|null, error: string|null}>}
 */
export async function checkGatewayHealth() {
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);
    const res = await fetch(`${GATEWAY_URL}/health`, {
      signal: controller.signal,
    });
    clearTimeout(timeout);
    const data = await res.json();
    return { ok: res.ok, data, error: null };
  } catch (err) {
    return {
      ok: false,
      data: null,
      error: err.name === 'AbortError' ? 'Timeout (5s)' : err.message,
    };
  }
}

export { BACKEND_URL, GATEWAY_URL };
