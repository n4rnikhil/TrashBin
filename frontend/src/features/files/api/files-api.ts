import { apiFetch } from "@/lib/api/client";
import type {
  FileDetail,
  FileSummary,
  PageResponse,
  UpdateFileRequest,
  UploadMetadata,
} from "@/lib/api/types";
import { uploadMultipart, type UploadOptions } from "@/lib/api/upload";

export type FileSort = "archivedAt,desc" | "archivedAt,asc" | "displayName,asc" | "sizeBytes,desc";

export type ListFilesParams = { page: number; size: number; sort: FileSort; favorite?: boolean };

const fileUrl = (id: string) => `/api/v1/files/${encodeURIComponent(id)}`;

export const filesApi = {
  list: ({ page, size, sort, favorite }: ListFilesParams, signal?: AbortSignal) => {
    const query = new URLSearchParams({ page: String(page), size: String(size), sort });
    if (favorite !== undefined) query.set("favorite", String(favorite));
    return apiFetch<PageResponse<FileSummary>>(`/api/v1/files?${query}`, signal ? { signal } : {});
  },

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

  setFavorite: (id: string, favorite: boolean) =>
    apiFetch<FileDetail>(`${fileUrl(id)}/favorite`, { method: "PUT", body: { favorite } }),
};

export const fileKeys = {
  all: ["files"] as const,
  lists: ["files", "list"] as const,
  list: (params: ListFilesParams) => ["files", "list", params] as const,
  detail: (id: string) => ["files", "detail", id] as const,
};
