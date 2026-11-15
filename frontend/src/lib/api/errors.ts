export type FieldError = { field: string; message: string };

/** A failed API call, parsed from the backend's RFC 9457 problem+json body. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    /** Stable machine-readable code, e.g. `INVALID_CREDENTIALS`. */
    readonly code: string,
    message: string,
    readonly traceId?: string,
    readonly fieldErrors: readonly FieldError[] = [],
  ) {
    super(message);
    this.name = "ApiError";
  }
}

const FALLBACK_MESSAGE = "Something went wrong. Please try again.";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function parseFieldErrors(value: unknown): FieldError[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((entry) =>
    isRecord(entry) && typeof entry["field"] === "string" && typeof entry["message"] === "string"
      ? [{ field: entry["field"], message: entry["message"] }]
      : [],
  );
}

/** Builds an ApiError from a non-OK response. Never throws; tolerates non-JSON bodies. */
export async function toApiError(response: Response): Promise<ApiError> {
  let body: unknown;
  try {
    body = await response.json();
  } catch {
    body = undefined;
  }
  if (!isRecord(body)) return new ApiError(response.status, "UNKNOWN", FALLBACK_MESSAGE);
  return new ApiError(
    response.status,
    typeof body["code"] === "string" ? body["code"] : "UNKNOWN",
    typeof body["detail"] === "string" ? body["detail"] : FALLBACK_MESSAGE,
    typeof body["traceId"] === "string" ? body["traceId"] : undefined,
    parseFieldErrors(body["errors"]),
  );
}

/** Message safe to show a user for any thrown value. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.status >= 500 && error.traceId
      ? `${error.message} (reference ${error.traceId.slice(0, 8)})`
      : error.message;
  }
  return "Could not reach the server. Check your connection and try again.";
}
