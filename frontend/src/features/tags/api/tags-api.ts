import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { CreateTagRequest, TagSummary, UpdateTagRequest } from "@/lib/api/types";

const tagUrl = (id: string) => `/api/v1/tags/${encodeURIComponent(id)}`;

export const tagsApi = {
  list: (signal?: AbortSignal) => apiFetch<TagSummary[]>("/api/v1/tags", signal ? { signal } : {}),
  create: (body: CreateTagRequest) =>
    apiFetch<TagSummary>("/api/v1/tags", { method: "POST", body }),
  update: (id: string, body: UpdateTagRequest) =>
    apiFetch<TagSummary>(tagUrl(id), { method: "PATCH", body }),
  remove: (id: string) => apiFetch<void>(tagUrl(id), { method: "DELETE" }),
  merge: (sourceId: string, targetTagId: string) =>
    apiFetch<TagSummary>(`${tagUrl(sourceId)}/merge`, { method: "POST", body: { targetTagId } }),
};

export const tagKeys = { all: ["tags"] as const };

/** All of the user's tags with file counts. */
export function useTags() {
  return useQuery({ queryKey: tagKeys.all, queryFn: ({ signal }) => tagsApi.list(signal) });
}
