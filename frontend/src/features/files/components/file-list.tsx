"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Archive, ChevronLeft, ChevronRight } from "lucide-react";
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
  const params = { page, size: PAGE_SIZE, sort };

  const files = useQuery({
    queryKey: fileKeys.list(params),
    queryFn: ({ signal }) => filesApi.list(params, signal),
    placeholderData: keepPreviousData,
  });

  const navigate = (next: { page?: number; sort?: FileSort }) => {
    const query = new URLSearchParams(searchParams);
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

      {files.isPending ? (
        <ListSkeleton rows={6} />
      ) : files.isError ? (
        <div role="alert" className="text-destructive text-sm">
          {errorMessage(files.error)}
        </div>
      ) : files.data.content.length === 0 ? (
        <EmptyState
          icon={Archive}
          title={page > 0 ? "No files on this page" : "Your archive is empty"}
          description={
            page > 0
              ? "Go back to the first page."
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
          <Text as="span" className="truncate font-medium">
            {file.displayName}
          </Text>
          <Text as="span" variant="caption" tone="muted" className="font-normal">
            {formatBytes(file.sizeBytes)} · Archived {formatDate(file.archivedAt)}
          </Text>
        </div>
        <span className="text-muted-foreground type-caption hidden font-normal sm:inline">
          {TYPE_LABELS[file.typeGroup]}
        </span>
      </Link>
    </li>
  );
}
