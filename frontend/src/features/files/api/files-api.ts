import { apiFetch } from "@/lib/api/client";
import type {
  AccessTicket,
  FileDetail,
  FileEvent,
  FileStatus,
  IntegrityResult,
  RediscoverMode,
  Stats,
  TicketPurpose,
  FileSummary,
  PageResponse,
  TypeGroup,
  UpdateFileRequest,
  UploadMetadata,
} from "@/lib/api/types";
import { uploadMultipart, type UploadOptions } from "@/lib/api/upload";

export type FileSort =
  "relevance" | "archivedAt,desc" | "archivedAt,asc" | "displayName,asc" | "sizeBytes,desc";

/** Query of GET /api/v1/files. Omitted values mean "no filter" / server default. */
export type ListFilesParams = {
  page: number;
  size: number;
  sort?: FileSort;
  /** Defaults to ARCHIVED on the server. */
  status?: FileStatus;
  q?: string;
  favorite?: boolean;
  typeGroup?: TypeGroup;
  tags?: string[];
  collection?: string;
  /** Inclusive dates (YYYY-MM-DD) over the archive date. */
  from?: string;
  to?: string;
};

const fileUrl = (id: string) => `/api/v1/files/${encodeURIComponent(id)}`;

export function listQueryString(params: ListFilesParams): string {
  const query = new URLSearchParams({ page: String(params.page), size: String(params.size) });
  if (params.sort) query.set("sort", params.sort);
  if (params.status) query.set("status", params.status);
  if (params.q) query.set("q", params.q);
  if (params.favorite !== undefined) query.set("favorite", String(params.favorite));
  if (params.typeGroup) query.set("typeGroup", params.typeGroup);
  params.tags?.forEach((tag) => query.append("tag", tag));
  if (params.collection) query.set("collection", params.collection);
  if (params.from) query.set("from", params.from);
  if (params.to) query.set("to", params.to);
  return query.toString();
}

export const filesApi = {
  list: (params: ListFilesParams, signal?: AbortSignal) =>
    apiFetch<PageResponse<FileSummary>>(
      `/api/v1/files?${listQueryString(params)}`,
      signal ? { signal } : {},
    ),

  get: (id: string, signal?: AbortSignal) =>
    apiFetch<FileDetail>(fileUrl(id), signal ? { signal } : {}),

  upload: (file: File, metadata: UploadMetadata, options: UploadOptions) => {
    const form = new FormData();
    form.append("file", file, file.name);
    form.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }));
    return uploadMultipart<FileDetail>("/api/v1/files", form, options);
  },

  /** `version` is the one the user saw; a newer server version yields VERSION_CONFLICT (412). */
  update: (id: string, version: number, body: UpdateFileRequest) =>
    apiFetch<FileDetail>(fileUrl(id), {
      method: "PATCH",
      body,
      headers: { "If-Match": `"${version}"` },
    }),

  setTags: (id: string, tags: string[]) =>
    apiFetch<FileDetail>(`${fileUrl(id)}/tags`, { method: "PUT", body: { tags } }),

  setCollections: (id: string, collectionIds: string[]) =>
    apiFetch<FileDetail>(`${fileUrl(id)}/collections`, {
      method: "PUT",
      body: { collectionIds },
    }),

  discard: (id: string) => apiFetch<FileDetail>(`${fileUrl(id)}/discard`, { method: "POST" }),

  restore: (id: string) => apiFetch<FileDetail>(`${fileUrl(id)}/restore`, { method: "POST" }),

  /** Permanent; the server only allows it for discarded files. */
  purge: (id: string) => apiFetch<void>(fileUrl(id), { method: "DELETE" }),

  verify: (id: string) => apiFetch<IntegrityResult>(`${fileUrl(id)}/verify`, { method: "POST" }),

  events: (id: string, signal?: AbortSignal) =>
    apiFetch<PageResponse<FileEvent>>(`${fileUrl(id)}/events?size=50`, signal ? { signal } : {}),

  rediscover: (mode: RediscoverMode, signal?: AbortSignal) =>
    apiFetch<FileSummary[]>(`/api/v1/rediscover?mode=${mode}`, signal ? { signal } : {}),

  stats: (signal?: AbortSignal) => apiFetch<Stats>("/api/v1/stats", signal ? { signal } : {}),

  /** Asks for a short-lived download or preview link (counts as one access). */
  createTicket: (id: string, purpose: TicketPurpose) =>
    apiFetch<AccessTicket>(`${fileUrl(id)}/access-tickets`, {
      method: "POST",
      body: { purpose },
    }),

  /** Reads a text preview through its link, keeping at most `maxChars` characters. */
  fetchText: async (url: string, maxChars: number, signal?: AbortSignal) => {
    const response = await fetch(url, { cache: "no-store", ...(signal ? { signal } : {}) });
    if (!response.ok) throw new Error(`Preview failed (${response.status})`);
    const text = await response.text();
    return { text: text.slice(0, maxChars), truncated: text.length > maxChars };
  },

  setFavorite: (id: string, favorite: boolean) =>
    apiFetch<FileDetail>(`${fileUrl(id)}/favorite`, { method: "PUT", body: { favorite } }),
};

export const fileKeys = {
  all: ["files"] as const,
  lists: ["files", "list"] as const,
  list: (params: ListFilesParams) => ["files", "list", params] as const,
  detail: (id: string) => ["files", "detail", id] as const,
  preview: (id: string) => ["files", "preview", id] as const,
  events: (id: string) => ["files", "events", id] as const,
  rediscover: (mode: RediscoverMode) => ["files", "rediscover", mode] as const,
  stats: ["files", "stats"] as const,
};
