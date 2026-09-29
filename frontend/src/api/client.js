/**
 * client.js
 * Shared fetch wrapper for all Spring Boot backend API calls.
 *
 * - Automatically prefixes BACKEND_URL
 * - 5-second timeout on every request
 * - Parses JSON responses
 * - Throws structured ApiError objects: { status, message, body }
 */

const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';

export class ApiError extends Error {
  constructor(status, message, body) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

/**
 * Core fetch wrapper.
 * @param {string} path   - Path relative to BACKEND_URL (e.g. '/api/apis')
 * @param {object} options - fetch options (method, body, headers, etc.)
 * @returns {Promise<any>} Parsed JSON response body
 * @throws {ApiError}
 */
export async function apiFetch(path, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 5000);

  const url = `${BACKEND_URL}${path}`;
  const headers = { 'Content-Type': 'application/json', ...options.headers };

  try {
    const res = await fetch(url, {
      ...options,
      headers,
      signal: controller.signal,
    });
    clearTimeout(timeout);

    let body = null;
    const ct = res.headers.get('content-type') || '';
    if (ct.includes('application/json')) {
      body = await res.json();
    } else {
      const text = await res.text();
      if (text) body = text;
    }

    if (!res.ok) {
      const msg =
        (body && (body.message || body.error || JSON.stringify(body))) ||
        `HTTP ${res.status}`;
      throw new ApiError(res.status, msg, body);
    }

    return body;
  } catch (err) {
    clearTimeout(timeout);
    if (err instanceof ApiError) throw err;
    if (err.name === 'AbortError') {
      throw new ApiError(0, 'Request timed out (5s)', null);
    }
    throw new ApiError(0, err.message || 'Network error', null);
  }
}

export { BACKEND_URL };
