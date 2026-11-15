import type { TypeGroup } from "@/lib/api/types";
import type { FileSort } from "./api/files-api";

/**
 * Archive view state, kept in the URL so every search is shareable and survives reload and
 * back/forward. Parsing is defensive: unknown values fall back to defaults instead of erroring.
 */
export type FileFilters = {
  q: string;
  tags: string[];
  collection: string | null;
  typeGroup: TypeGroup | null;
  from: string | null;
  to: string | null;
  favorite: boolean;
  /** Null means the default: best match when searching, otherwise newest first. */
  sort: FileSort | null;
  page: number;
};

export const TYPE_GROUPS: readonly TypeGroup[] = [
  "DOCUMENT",
  "IMAGE",
  "SPREADSHEET",
  "PRESENTATION",
  "TEXT",
  "AUDIO",
  "VIDEO",
  "ARCHIVE",
  "OTHER",
];

const SORTS: readonly FileSort[] = [
  "relevance",
  "archivedAt,desc",
  "archivedAt,asc",
  "displayName,asc",
  "sizeBytes,desc",
];

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const EMPTY_FILTERS: FileFilters = {
  q: "",
  tags: [],
  collection: null,
  typeGroup: null,
  from: null,
  to: null,
  favorite: false,
  sort: null,
  page: 0,
};

export function parseFilters(params: URLSearchParams): FileFilters {
  const typeGroup = params.get("type");
  const sort = params.get("sort");
  const from = params.get("from");
  const to = params.get("to");
  const collection = params.get("collection");
  return {
    q: (params.get("q") ?? "").slice(0, 200),
    tags: params
      .getAll("tag")
      .filter((t) => UUID.test(t))
      .slice(0, 10),
    collection: collection && UUID.test(collection) ? collection : null,
    typeGroup: TYPE_GROUPS.find((g) => g === typeGroup) ?? null,
    from: from && DATE.test(from) ? from : null,
    to: to && DATE.test(to) ? to : null,
    favorite: params.get("favorite") === "1",
    sort: SORTS.find((s) => s === sort) ?? null,
    page: Math.max(0, Number.parseInt(params.get("page") ?? "0", 10) || 0),
  };
}

/** Inverse of parseFilters; defaults are left out to keep URLs short. */
export function toSearchParams(filters: FileFilters): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.q) params.set("q", filters.q);
  filters.tags.forEach((t) => params.append("tag", t));
  if (filters.collection) params.set("collection", filters.collection);
  if (filters.typeGroup) params.set("type", filters.typeGroup);
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  if (filters.favorite) params.set("favorite", "1");
  if (filters.sort) params.set("sort", filters.sort);
  if (filters.page > 0) params.set("page", String(filters.page));
  return params;
}

/** The sort actually applied: an explicit choice, or the context-dependent default. */
export function effectiveSort(filters: FileFilters): FileSort {
  if (filters.sort === "relevance" && !filters.q) return "archivedAt,desc";
  return filters.sort ?? (filters.q ? "relevance" : "archivedAt,desc");
}

export function hasActiveFilters(filters: FileFilters): boolean {
  return Boolean(
    filters.q ||
    filters.tags.length ||
    filters.collection ||
    filters.typeGroup ||
    filters.from ||
    filters.to ||
    filters.favorite,
  );
}
