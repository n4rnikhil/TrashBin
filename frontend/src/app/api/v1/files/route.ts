import { parseServerEnv } from "@/lib/config/env";

/**
 * Streaming pass-through for /api/v1/files (uploads and listing).
 *
 * Every other /api/* path uses the next.config rewrite, but that proxy buffers request bodies in
 * memory and truncates them beyond 10 MB, which breaks large uploads. This handler streams the body
 * to the backend instead, so memory use stays flat whatever the file size. It takes precedence over
 * the rewrite because route handlers are matched before rewrites. Only data the backend needs is
 * forwarded; the backend still performs all authentication and validation.
 */

const { BACKEND_URL } = parseServerEnv(process.env);

const REQUEST_HEADERS = ["authorization", "content-type", "content-length", "accept"] as const;
const RESPONSE_HEADERS = [
  "content-type",
  "cache-control",
  "etag",
  "retry-after",
  "x-trace-id",
] as const;

function pick(source: Headers, names: readonly string[]): Headers {
  const result = new Headers();
  for (const name of names) {
    const value = source.get(name);
    if (value !== null) result.set(name, value);
  }
  return result;
}

async function forward(request: Request): Promise<Response> {
  const { search } = new URL(request.url);
  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  // `duplex: "half"` is required by Node's fetch to send a streamed body.
  const init: RequestInit & { duplex?: "half" } = {
    method: request.method,
    headers: pick(request.headers, REQUEST_HEADERS),
    redirect: "manual",
    cache: "no-store",
    ...(hasBody ? { body: request.body, duplex: "half" } : {}),
  };

  try {
    const response = await fetch(`${BACKEND_URL}/api/v1/files${search}`, init);
    return new Response(response.body, {
      status: response.status,
      headers: pick(response.headers, RESPONSE_HEADERS),
    });
  } catch {
    // Same shape as the backend's problem documents, so the client handles it uniformly.
    return Response.json(
      {
        type: "https://trashbin.local/problems/internal-error",
        title: "Backend unavailable",
        status: 502,
        code: "BACKEND_UNAVAILABLE",
        detail: "The server is not reachable. Please try again shortly.",
      },
      { status: 502, headers: { "content-type": "application/problem+json" } },
    );
  }
}

export const GET = forward;
export const POST = forward;
