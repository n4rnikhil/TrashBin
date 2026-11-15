"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Archive, ChevronLeft, ChevronRight, Star } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { EmptyState } from "@/components/patterns/empty-state";
import { ListSkeleton } from "@/components/patterns/skeletons";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Text } from "@/components/ui/typography";
import { TagChip } from "@/features/tags";
import { errorMessage } from "@/lib/api/errors";
import type { FileSummary } from "@/lib/api/types";
import { fileKeys, filesApi, type FileSort } from "../api/files-api";
import { formatBytes, formatDate } from "../format";
import { FileTypeIcon, TYPE_LABELS } from "./file-type-icon";

const PAGE_SIZE = 20;

const SORTS: { value: FileSort; label: string }[] = [
  { value: "archivedAt,desc", label: "Newest first" },
  { value: "archivedAt,asc", label: "Oldest first" },
  { value: "displayName,asc", label: "Name (A–Z)" },
  { value: "sizeBytes,desc", label: "Largest first" },
];

function isSort(value: string | null): value is FileSort {
  return SORTS.some((s) => s.value === value);
}

/** Paged archive listing. Page and sort live in the URL so views are shareable. */
export function FileList() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const rawSort = searchParams.get("sort");
  const sort: FileSort = isSort(rawSort) ? rawSort : "archivedAt,desc";
  const page = Math.max(0, Number.parseInt(searchParams.get("page") ?? "0", 10) || 0);
  const favoritesOnly = searchParams.get("favorite") === "1";
  const params = { page, size: PAGE_SIZE, sort, ...(favoritesOnly ? { favorite: true } : {}) };

  const files = useQuery({
    queryKey: fileKeys.list(params),
    queryFn: ({ signal }) => filesApi.list(params, signal),
    placeholderData: keepPreviousData,
  });

  const navigate = (next: { page?: number; sort?: FileSort; favorite?: boolean }) => {
    const query = new URLSearchParams(searchParams);
    if (next.favorite !== undefined) {
      if (next.favorite) query.set("favorite", "1");
      else query.delete("favorite");
      query.delete("page");
    }
    if (next.sort !== undefined) {
      query.set("sort", next.sort);
      query.delete("page");
    }
    if (next.page !== undefined) {
      if (next.page === 0) query.delete("page");
      else query.set("page", String(next.page));
    }
    const qs = query.toString();
    router.push(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
  };

  const total = files.data?.totalElements ?? 0;
  const totalPages = files.data?.totalPages ?? 0;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <Text tone="muted" aria-live="polite">
          {files.data ? `${total} ${total === 1 ? "file" : "files"}` : " "}
        </Text>
        <div className="flex items-center gap-2">
          <Button
            variant={favoritesOnly ? "secondary" : "outline"}
            size="sm"
            aria-pressed={favoritesOnly}
            onClick={() => navigate({ favorite: !favoritesOnly })}
          >
            <Star className={favoritesOnly ? "fill-warning text-warning" : undefined} aria-hidden />
            Favorites
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger render={<Button variant="outline" size="sm" />}>
              {SORTS.find((s) => s.value === sort)?.label}
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-44">
              <DropdownMenuRadioGroup
                value={sort}
                onValueChange={(value: string) => {
                  if (isSort(value)) navigate({ sort: value });
                }}
              >
                {SORTS.map((s) => (
                  <DropdownMenuRadioItem key={s.value} value={s.value}>
                    {s.label}
                  </DropdownMenuRadioItem>
                ))}
              </DropdownMenuRadioGroup>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      {files.isPending ? (
        <ListSkeleton rows={6} />
      ) : files.isError ? (
        <div role="alert" className="text-destructive text-sm">
          {errorMessage(files.error)}
        </div>
      ) : files.data.content.length === 0 ? (
        <EmptyState
          icon={Archive}
          title={
            page > 0
              ? "No files on this page"
              : favoritesOnly
                ? "No favorites yet"
                : "Your archive is empty"
          }
          description={
            page > 0
              ? "Go back to the first page."
              : favoritesOnly
                ? "Mark a file as a favorite from its detail page to find it here."
                : "Upload something above. Files you archive are kept, searchable and restorable."
          }
        />
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
            disabled={page === 0}
            onClick={() => navigate({ page: page - 1 })}
          >
            <ChevronLeft aria-hidden /> Previous
          </Button>
          <Text variant="caption" tone="muted" className="font-normal">
            Page {page + 1} of {totalPages}
          </Text>
          <Button
            variant="outline"
            size="sm"
            disabled={page + 1 >= totalPages}
            onClick={() => navigate({ page: page + 1 })}
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
