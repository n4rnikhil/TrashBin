"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Archive, ChevronLeft, ChevronRight, SearchX, Star } from "lucide-react";
import Link from "next/link";
import { EmptyState } from "@/components/patterns/empty-state";
import { ListSkeleton } from "@/components/patterns/skeletons";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/typography";
import { TagChip } from "@/features/tags";
import { errorMessage } from "@/lib/api/errors";
import type { FileSummary } from "@/lib/api/types";
import { fileKeys, filesApi, type ListFilesParams } from "../api/files-api";
import { effectiveSort, hasActiveFilters, type FileFilters } from "../filters";
import { formatBytes, formatDate } from "../format";
import { useFileFilters } from "../hooks/use-file-filters";
import { FileFilterBar } from "./file-filter-bar";
import { FileTypeIcon, TYPE_LABELS } from "./file-type-icon";

const PAGE_SIZE = 20;

export function toListParams(filters: FileFilters): ListFilesParams {
  return {
    page: filters.page,
    size: PAGE_SIZE,
    sort: effectiveSort(filters),
    ...(filters.q ? { q: filters.q } : {}),
    ...(filters.favorite ? { favorite: true } : {}),
    ...(filters.typeGroup ? { typeGroup: filters.typeGroup } : {}),
    ...(filters.tags.length ? { tags: filters.tags } : {}),
    ...(filters.collection ? { collection: filters.collection } : {}),
    ...(filters.from ? { from: filters.from } : {}),
    ...(filters.to ? { to: filters.to } : {}),
  };
}

/** Search, filter and page through the archive. All state lives in the URL. */
export function FileList() {
  const { filters, update, clear } = useFileFilters();
  const params = toListParams(filters);
  const files = useQuery({
    queryKey: fileKeys.list(params),
    queryFn: ({ signal }) => filesApi.list(params, signal),
    placeholderData: keepPreviousData,
  });

  const total = files.data?.totalElements ?? 0;
  const totalPages = files.data?.totalPages ?? 0;
  const filtered = hasActiveFilters(filters);

  return (
    <div className="flex flex-col gap-4">
      <FileFilterBar filters={filters} update={update} clear={clear} />

      <Text tone="muted" aria-live="polite">
        {files.data
          ? filters.q
            ? `${total} ${total === 1 ? "result" : "results"} for “${filters.q}”`
            : `${total} ${total === 1 ? "file" : "files"}`
          : " "}
      </Text>

      {files.isPending ? (
        <ListSkeleton rows={6} />
      ) : files.isError ? (
        <div role="alert" className="text-destructive text-sm">
          {errorMessage(files.error)}
        </div>
      ) : files.data.content.length === 0 ? (
        filtered ? (
          <EmptyState
            icon={SearchX}
            title="No files match"
            description="Try other words or remove a filter."
            action={
              <Button variant="outline" onClick={clear}>
                Clear filters
              </Button>
            }
          />
        ) : (
          <EmptyState
            icon={Archive}
            title={filters.page > 0 ? "No files on this page" : "Your archive is empty"}
            description={
              filters.page > 0
                ? "Go back to the first page."
                : "Upload something above. Files you archive are kept, searchable and restorable."
            }
          />
        )
      ) : (
        <ul className="divide-border flex flex-col divide-y" aria-busy={files.isFetching}>
          {files.data.content.map((file) => (
            <FileRow key={file.id} file={file} />
          ))}
        </ul>
      )}

      {totalPages > 1 ? (
        <nav className="flex items-center justify-between gap-3" aria-label="Pagination">
          <Button
            variant="outline"
            size="sm"
            disabled={filters.page === 0}
            onClick={() => update({ page: filters.page - 1 })}
          >
            <ChevronLeft aria-hidden /> Previous
          </Button>
          <Text variant="caption" tone="muted" className="font-normal">
            Page {filters.page + 1} of {totalPages}
          </Text>
          <Button
            variant="outline"
            size="sm"
            disabled={filters.page + 1 >= totalPages}
            onClick={() => update({ page: filters.page + 1 })}
          >
            Next <ChevronRight aria-hidden />
          </Button>
        </nav>
      ) : null}
    </div>
  );
}

export function FileRow({ file }: { file: FileSummary }) {
  return (
    <li>
      <Link
        href={`/files/${file.id}`}
        className="hover:bg-secondary/60 focus-visible:bg-secondary/60 -mx-2 flex items-center gap-3 rounded-lg px-2 py-3 transition-colors"
      >
        <FileTypeIcon typeGroup={file.typeGroup} />
        <div className="flex min-w-0 flex-1 flex-col">
          <Text as="span" className="flex items-center gap-1.5 font-medium">
            <span className="truncate">{file.displayName}</span>
            {file.favorite ? (
              <Star className="fill-warning text-warning size-3.5 shrink-0" aria-label="Favorite" />
            ) : null}
          </Text>
          <Text as="span" variant="caption" tone="muted" className="font-normal">
            {formatBytes(file.sizeBytes)} · Archived {formatDate(file.archivedAt)}
          </Text>
        </div>
        {file.tags.length > 0 ? (
          <span className="hidden max-w-[40%] flex-wrap justify-end gap-1 md:flex">
            {file.tags.slice(0, 3).map((tag) => (
              <TagChip key={tag.id} name={tag.name} color={tag.color} />
            ))}
            {file.tags.length > 3 ? (
              <span className="text-muted-foreground type-caption font-normal">
                +{file.tags.length - 3}
              </span>
            ) : null}
          </span>
        ) : (
          <span className="text-muted-foreground type-caption hidden font-normal sm:inline">
            {TYPE_LABELS[file.typeGroup]}
          </span>
        )}
      </Link>
    </li>
  );
}
