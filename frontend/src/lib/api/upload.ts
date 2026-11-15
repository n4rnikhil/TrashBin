import { refreshSession, tokenStore } from "./client";
import { apiErrorFrom, parseJsonSafely } from "./errors";

export type UploadOptions = {
  /** Called with a fraction between 0 and 1 as bytes leave the browser. */
  onProgress?: (fraction: number) => void;
  signal?: AbortSignal;
};

type RawResponse = { status: number; text: string };

/** Thrown when the caller aborted the upload. */
export class UploadCanceledError extends Error {
  constructor() {
    super("Upload canceled");
    this.name = "UploadCanceledError";
  }
}

function send(path: string, form: FormData, options: UploadOptions): Promise<RawResponse> {
  return new Promise((resolve, reject) => {
    if (options.signal?.aborted) {
      reject(new UploadCanceledError());
      return;
    }
    const xhr = new XMLHttpRequest();
    xhr.open("POST", path);
    xhr.setRequestHeader("Accept", "application/json");
    const token = tokenStore.get();
    if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && event.total > 0) {
        options.onProgress?.(event.loaded / event.total);
      }
    };
    xhr.onload = () => resolve({ status: xhr.status, text: xhr.responseText });
    xhr.onerror = () => reject(new TypeError("Network error during upload"));
    xhr.onabort = () => reject(new UploadCanceledError());
    options.signal?.addEventListener("abort", () => xhr.abort(), { once: true });
    xhr.send(form);
  });
}

/**
 * Multipart upload with progress and cancellation. `fetch` cannot report upload progress, hence
 * XMLHttpRequest. Like apiFetch, it renews an expired session once and retries.
 */
export async function uploadMultipart<T>(
  path: string,
  form: FormData,
  options: UploadOptions = {},
): Promise<T> {
  let response = await send(path, form, options);
  if (response.status === 401) {
    const error = apiErrorFrom(401, parseJsonSafely(response.text));
    if (error.code !== "UNAUTHENTICATED" || !(await refreshSession())) throw error;
    options.onProgress?.(0);
    response = await send(path, form, options);
  }
  const body = parseJsonSafely(response.text);
  if (response.status < 200 || response.status >= 300) throw apiErrorFrom(response.status, body);
  return body as T;
}
