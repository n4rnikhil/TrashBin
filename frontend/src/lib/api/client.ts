const DEFAULT_TIMEOUT_MS = 5000;

/**
 * Thin fetch wrapper for the backend API. All HTTP from the app goes through here (never call fetch
 * directly from components). Authentication (bearer token, refresh-and-retry) is added in M1.
 *
 * Requests use relative URLs: Next.js proxies /api/* to the backend, so the browser stays on one origin.
 */
export async function apiRequest(path: string, init: RequestInit = {}): Promise<Response> {
  return fetch(path, {
    ...init,
    headers: { Accept: "application/json", ...init.headers },
    signal: init.signal ?? AbortSignal.timeout(DEFAULT_TIMEOUT_MS),
    cache: "no-store",
  });
}
