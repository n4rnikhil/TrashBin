import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type {
  CollectionSummary,
  CreateCollectionRequest,
  UpdateCollectionRequest,
} from "@/lib/api/types";

const collectionUrl = (id: string) => `/api/v1/collections/${encodeURIComponent(id)}`;

export const collectionsApi = {
  list: (signal?: AbortSignal) =>
    apiFetch<CollectionSummary[]>("/api/v1/collections", signal ? { signal } : {}),
  create: (body: CreateCollectionRequest) =>
    apiFetch<CollectionSummary>("/api/v1/collections", { method: "POST", body }),
  update: (id: string, body: UpdateCollectionRequest) =>
    apiFetch<CollectionSummary>(collectionUrl(id), { method: "PATCH", body }),
  remove: (id: string) => apiFetch<void>(collectionUrl(id), { method: "DELETE" }),
};

export const collectionKeys = { all: ["collections"] as const };

/** All of the user's collections with file counts. */
export function useCollections() {
  return useQuery({
    queryKey: collectionKeys.all,
    queryFn: ({ signal }) => collectionsApi.list(signal),
  });
}
