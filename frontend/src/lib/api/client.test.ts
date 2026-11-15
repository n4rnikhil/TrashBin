import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch, onSessionExpired, refreshSession, tokenStore } from "./client";
import { ApiError } from "./errors";

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const problem = (status: number, code: string) =>
  json({ status, code, detail: `${code} detail`, traceId: "trace-1" }, status);

type Handler = (url: string, init: RequestInit) => Response;

function stubFetch(handler: Handler) {
  const mock = vi.fn((url: string, init: RequestInit = {}) => Promise.resolve(handler(url, init)));
  vi.stubGlobal("fetch", mock);
  return mock;
}

const authHeader = (init: RequestInit) =>
  (init.headers as Record<string, string> | undefined)?.["Authorization"];

describe("apiFetch", () => {
  beforeEach(() => tokenStore.set(null));
  afterEach(() => vi.unstubAllGlobals());

  it("sends the bearer token and returns parsed JSON", async () => {
    tokenStore.set("token-1");
    const fetchMock = stubFetch(() => json({ ok: true }));

    await expect(apiFetch("/api/v1/users/me")).resolves.toEqual({ ok: true });
    expect(authHeader(fetchMock.mock.calls[0]?.[1] ?? {})).toBe("Bearer token-1");
  });

  it("renews the session once and retries when the access token expired", async () => {
    tokenStore.set("stale");
    const fetchMock = stubFetch((url, init) => {
      if (url.endsWith("/auth/refresh")) return json({ accessToken: "fresh", user: {} });
      return authHeader(init) === "Bearer fresh"
        ? json({ id: 1 })
        : problem(401, "UNAUTHENTICATED");
    });

    await expect(apiFetch("/api/v1/users/me")).resolves.toEqual({ id: 1 });
    expect(tokenStore.get()).toBe("fresh");
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("shares one refresh between concurrent requests", async () => {
    tokenStore.set("stale");
    let refreshCalls = 0;
    stubFetch((url, init) => {
      if (url.endsWith("/auth/refresh")) {
        refreshCalls++;
        return json({ accessToken: "fresh", user: {} });
      }
      return authHeader(init) === "Bearer fresh" ? json({}) : problem(401, "UNAUTHENTICATED");
    });

    await Promise.all([apiFetch("/a"), apiFetch("/b"), apiFetch("/c")]);

    expect(refreshCalls).toBe(1);
  });

  it("does not try to renew for wrong credentials", async () => {
    const fetchMock = stubFetch(() => problem(401, "INVALID_CREDENTIALS"));

    await expect(
      apiFetch("/api/v1/auth/login", { method: "POST", body: {} }),
    ).rejects.toMatchObject({
      code: "INVALID_CREDENTIALS",
      status: 401,
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("notifies listeners and clears the token when the session cannot be renewed", async () => {
    tokenStore.set("stale");
    stubFetch((url) =>
      url.endsWith("/auth/refresh")
        ? problem(401, "INVALID_REFRESH_TOKEN")
        : problem(401, "UNAUTHENTICATED"),
    );
    const expired = vi.fn();
    const unsubscribe = onSessionExpired(expired);

    await expect(apiFetch("/api/v1/users/me")).rejects.toBeInstanceOf(ApiError);

    expect(expired).toHaveBeenCalledTimes(1);
    expect(tokenStore.get()).toBeNull();
    unsubscribe();
  });

  it("exposes field errors and the trace id from problem documents", async () => {
    stubFetch(() =>
      json(
        {
          status: 400,
          code: "VALIDATION_FAILED",
          detail: "Invalid.",
          traceId: "t-9",
          errors: [{ field: "email", message: "bad" }],
        },
        400,
      ),
    );

    const error = await apiFetch("/x", { method: "POST", body: {} }).catch((e: unknown) => e);

    expect(error).toMatchObject({ code: "VALIDATION_FAILED", traceId: "t-9" });
    expect((error as ApiError).fieldErrors).toEqual([{ field: "email", message: "bad" }]);
  });

  it("returns undefined for 204 responses", async () => {
    stubFetch(() => new Response(null, { status: 204 }));

    await expect(apiFetch<void>("/x", { method: "DELETE" })).resolves.toBeUndefined();
  });
});

describe("refreshSession", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("sends the CSRF guard header and treats network failure as signed out", async () => {
    const fetchMock = stubFetch(() => json({ accessToken: "a", user: {} }));
    await refreshSession();
    expect(
      (fetchMock.mock.calls[0]?.[1]?.headers as Record<string, string>)["X-Requested-With"],
    ).toBe("trashbin");

    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.reject(new TypeError("offline"))),
    );
    await expect(refreshSession()).resolves.toBeNull();
    expect(tokenStore.get()).toBeNull();
  });
});
