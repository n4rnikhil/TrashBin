import type { components } from "@/lib/api/generated/schema";
import { apiRequest } from "@/lib/api/client";

export type HealthResponse = components["schemas"]["HealthResponse"];

/** Result of probing the backend through the Next.js proxy. */
export type BackendStatus =
  | { kind: "up"; health: HealthResponse }
  | { kind: "degraded"; health: HealthResponse }
  | { kind: "unreachable" };

function isHealthResponse(value: unknown): value is HealthResponse {
  if (typeof value !== "object" || value === null) return false;
  const record = value as Record<string, unknown>;
  return typeof record["status"] === "string" && typeof record["database"] === "string";
}

/**
 * Probes GET /api/v1/health. HTTP 200 means healthy, 503 with a health body means the backend is
 * running but a dependency is down; anything else (network error, proxy error, bad body) means the
 * backend is unreachable.
 */
export async function getBackendStatus(signal?: AbortSignal): Promise<BackendStatus> {
  try {
    const response = await apiRequest("/api/v1/health", signal ? { signal } : {});
    if (response.status !== 200 && response.status !== 503) return { kind: "unreachable" };
    const body: unknown = await response.json();
    if (!isHealthResponse(body)) return { kind: "unreachable" };
    return response.status === 200
      ? { kind: "up", health: body }
      : { kind: "degraded", health: body };
  } catch {
    // Network failure, timeout, abort or non-JSON body: all mean "cannot talk to the backend".
    return { kind: "unreachable" };
  }
}
