import { apiFetch } from "@/lib/api/client";
import type { FileDetail, FileSummary, PageResponse, UploadMetadata } from "@/lib/api/types";
import { uploadMultipart, type UploadOptions } from "@/lib/api/upload";

export type FileSort = "archivedAt,desc" | "archivedAt,asc" | "displayName,asc" | "sizeBytes,desc";

export type ListFilesParams = { page: number; size: number; sort: FileSort };

export const filesApi = {
  list: ({ page, size, sort }: ListFilesParams, signal?: AbortSignal) => {
    const query = new URLSearchParams({ page: String(page), size: String(size), sort });
    return apiFetch<PageResponse<FileSummary>>(`/api/v1/files?${query}`, signal ? { signal } : {});
  },

  get: (id: string, signal?: AbortSignal) =>
    apiFetch<FileDetail>(`/api/v1/files/${encodeURIComponent(id)}`, signal ? { signal } : {}),

  upload: (file: File, metadata: UploadMetadata, options: UploadOptions) => {
    const form = new FormData();
    form.append("file", file, file.name);
    form.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }));
    return uploadMultipart<FileDetail>("/api/v1/files", form, options);
  },
};

export const fileKeys = {
  all: ["files"] as const,
  list: (params: ListFilesParams) => ["files", "list", params] as const,
  detail: (id: string) => ["files", "detail", id] as const,
};
