import { afterEach, describe, expect, it, vi } from "vitest";
import { getBackendStatus } from "./get-health";

function mockFetch(response: Response | Error) {
  const fn = vi.fn(() =>
    response instanceof Error ? Promise.reject(response) : Promise.resolve(response),
  );
  vi.stubGlobal("fetch", fn);
  return fn;
}

const json = (body: unknown, status: number) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

describe("getBackendStatus", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("reports up for a healthy backend", async () => {
    const health = { status: "UP", database: "UP", timestamp: "2026-11-15T10:15:30Z" };
    const fetchMock = mockFetch(json(health, 200));

    await expect(getBackendStatus()).resolves.toEqual({ kind: "up", health });
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/health", expect.any(Object));
  });

  it("reports degraded when the backend answers 503 with a health body", async () => {
    const health = { status: "DOWN", database: "DOWN", timestamp: "2026-11-15T10:15:30Z" };
    mockFetch(json(health, 503));

    await expect(getBackendStatus()).resolves.toEqual({ kind: "degraded", health });
  });

  it("reports unreachable on network failure", async () => {
    mockFetch(new TypeError("fetch failed"));

    await expect(getBackendStatus()).resolves.toEqual({ kind: "unreachable" });
  });

  it("reports unreachable on a proxy error response", async () => {
    mockFetch(new Response("Internal Server Error", { status: 500 }));

    await expect(getBackendStatus()).resolves.toEqual({ kind: "unreachable" });
  });

  it("reports unreachable when the body is not a health payload", async () => {
    mockFetch(json({ unexpected: true }, 200));

    await expect(getBackendStatus()).resolves.toEqual({ kind: "unreachable" });
  });
});
