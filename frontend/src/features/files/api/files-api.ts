import { apiFetch } from "@/lib/api/client";
import type {
  FileDetail,
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

  setFavorite: (id: string, favorite: boolean) =>
    apiFetch<FileDetail>(`${fileUrl(id)}/favorite`, { method: "PUT", body: { favorite } }),
};

export const fileKeys = {
  all: ["files"] as const,
  lists: ["files", "list"] as const,
  list: (params: ListFilesParams) => ["files", "list", params] as const,
  detail: (id: string) => ["files", "detail", id] as const,
};
