"use client";

import { useQuery } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import Link from "next/link";
import { EmptyState } from "@/components/patterns/empty-state";
import { ListSkeleton } from "@/components/patterns/skeletons";
import { Text } from "@/components/ui/typography";
import { errorMessage } from "@/lib/api/errors";
import type { FileSummary } from "@/lib/api/types";
import { fileKeys, filesApi, type ListFilesParams } from "../api/files-api";
import { formatBytes, formatDate } from "../format";
import { FileTypeIcon } from "./file-type-icon";
import { daysUntil } from "./lifecycle-actions";

const PARAMS: ListFilesParams = {
  page: 0,
  size: 100,
  status: "DISCARDED",
  sort: "archivedAt,desc",
};

/** Files the user decided are trash; each can still be restored until its purge date. */
export function DiscardedList() {
  const files = useQuery({
    queryKey: fileKeys.list(PARAMS),
    queryFn: ({ signal }) => filesApi.list(PARAMS, signal),
  });

  if (files.isPending) return <ListSkeleton rows={4} />;
  if (files.isError)
    return (
      <div role="alert" className="text-destructive text-sm">
        {errorMessage(files.error)}
      </div>
    );
  if (files.data.content.length === 0)
    return (
      <EmptyState
        icon={Trash2}
        title="Nothing discarded"
        description="When you discard a file it waits here, restorable, before it is deleted for good."
      />
    );

  return (
    <ul className="divide-border flex flex-col divide-y">
      {files.data.content.map((file) => (
        <DiscardedRow key={file.id} file={file} />
      ))}
    </ul>
  );
}

function DiscardedRow({ file }: { file: FileSummary }) {
  return (
    <li>
      <Link
        href={`/files/${file.id}`}
        className="hover:bg-secondary/60 -mx-2 flex items-center gap-3 rounded-lg px-2 py-3 transition-colors"
      >
        <FileTypeIcon typeGroup={file.typeGroup} />
        <div className="flex min-w-0 flex-1 flex-col">
          <Text as="span" className="truncate font-medium">
            {file.displayName}
          </Text>
          <Text as="span" variant="caption" tone="muted" className="font-normal">
            {formatBytes(file.sizeBytes)}
            {file.purgeAfter
              ? ` · Deleted on ${formatDate(file.purgeAfter)}`
              : " · Kept until you delete it"}
          </Text>
        </div>
        {file.purgeAfter ? (
          <span className="text-warning type-caption shrink-0">
            {daysUntil(file.purgeAfter)} days left
          </span>
        ) : null}
      </Link>
    </li>
  );
}
