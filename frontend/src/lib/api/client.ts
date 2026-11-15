import { toApiError } from "./errors";
import type { AuthResponse } from "./types";

const DEFAULT_TIMEOUT_MS = 15_000;
/** Required by the backend on cookie-authenticated endpoints (CSRF guard). */
const COOKIE_GUARD_HEADER = { "X-Requested-With": "trashbin" } as const;

/* ------------------------------------------------------------------------------------------------
 * Access token: held in memory only. Never write it to localStorage, sessionStorage or a cookie.
 * ---------------------------------------------------------------------------------------------- */
let accessToken: string | null = null;

export const tokenStore = {
  get: () => accessToken,
  set: (token: string | null) => {
    accessToken = token;
  },
};

type Listener = () => void;
const sessionExpiredListeners = new Set<Listener>();

/** Subscribe to "the session could not be renewed". Returns an unsubscribe function. */
export function onSessionExpired(listener: Listener): () => void {
  sessionExpiredListeners.add(listener);
  return () => sessionExpiredListeners.delete(listener);
}

/* ------------------------------------------------------------------------------------------------
 * Low-level request. Relative URLs only: Next.js proxies /api/* to the backend.
 * ---------------------------------------------------------------------------------------------- */
export async function apiRequest(path: string, init: RequestInit = {}): Promise<Response> {
  return fetch(path, {
    ...init,
    headers: { Accept: "application/json", ...init.headers },
    signal: init.signal ?? AbortSignal.timeout(DEFAULT_TIMEOUT_MS),
    cache: "no-store",
  });
}

/* ------------------------------------------------------------------------------------------------
 * Session renewal. Single-flight within a tab, and serialized across tabs with the Web Locks API
 * so two tabs never rotate the same refresh token at the same moment.
 * ---------------------------------------------------------------------------------------------- */
let refreshInFlight: Promise<AuthResponse | null> | null = null;

async function doRefresh(): Promise<AuthResponse | null> {
  try {
    const response = await apiRequest("/api/v1/auth/refresh", {
      method: "POST",
      headers: COOKIE_GUARD_HEADER,
    });
    if (!response.ok) {
      tokenStore.set(null);
      return null;
    }
    const body = (await response.json()) as AuthResponse;
    tokenStore.set(body.accessToken);
    return body;
  } catch {
    // Network failure: we cannot prove the session is valid, so treat it as signed out.
    tokenStore.set(null);
    return null;
  }
}

/** Exchanges the refresh cookie for a new access token. Resolves to null when signed out. */
export function refreshSession(): Promise<AuthResponse | null> {
  if (refreshInFlight) return refreshInFlight;
  const exclusive = async (): Promise<AuthResponse | null> =>
    typeof navigator !== "undefined" && "locks" in navigator
      ? await navigator.locks.request("trashbin-refresh", () => doRefresh())
      : await doRefresh();
  const pending = exclusive().finally(() => {
    refreshInFlight = null;
  });
  refreshInFlight = pending;
  return pending;
}

/* ------------------------------------------------------------------------------------------------
 * Typed JSON API call with bearer auth and one transparent renew-and-retry.
 * ---------------------------------------------------------------------------------------------- */
export type ApiFetchOptions = {
  method?: "GET" | "POST" | "PATCH" | "PUT" | "DELETE";
  body?: unknown;
  signal?: AbortSignal;
  /** Send the CSRF guard header (cookie-authenticated endpoints only). */
  cookieGuard?: boolean;
  /** Extra request headers (e.g. If-Match). Auth and content headers are managed here. */
  headers?: Record<string, string>;
};

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const send = () => {
    const token = tokenStore.get();
    return apiRequest(path, {
      method: options.method ?? "GET",
      headers: {
        ...options.headers,
        ...(options.body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.cookieGuard ? COOKIE_GUARD_HEADER : {}),
      },
      ...(options.body !== undefined ? { body: JSON.stringify(options.body) } : {}),
      ...(options.signal ? { signal: options.signal } : {}),
    });
  };

  let response = await send();
  if (response.status === 401) {
    const error = await toApiError(response);
    // Only an expired/missing access token is renewable; wrong credentials etc. are final.
    if (error.code !== "UNAUTHENTICATED") throw error;
    if (!(await refreshSession())) {
      sessionExpiredListeners.forEach((listener) => listener());
      throw error;
    }
    response = await send();
  }
  if (!response.ok) throw await toApiError(response);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}
