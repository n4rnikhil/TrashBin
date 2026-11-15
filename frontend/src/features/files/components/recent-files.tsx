"use client";

import { useQuery } from "@tanstack/react-query";
import { Archive } from "lucide-react";
import Link from "next/link";
import { EmptyState } from "@/components/patterns/empty-state";
import { ListSkeleton } from "@/components/patterns/skeletons";
import { Button } from "@/components/ui/button";
import { errorMessage } from "@/lib/api/errors";
import { fileKeys, filesApi, type ListFilesParams } from "../api/files-api";
import { FileRow } from "./file-list";

const RECENT: ListFilesParams = { page: 0, size: 5, sort: "archivedAt,desc" };

/** The five most recently archived files, for the overview page. */
export function RecentFiles() {
  const files = useQuery({
    queryKey: fileKeys.list(RECENT),
    queryFn: ({ signal }) => filesApi.list(RECENT, signal),
  });

  if (files.isPending) return <ListSkeleton rows={3} />;
  if (files.isError)
    return (
      <div role="alert" className="text-destructive text-sm">
        {errorMessage(files.error)}
      </div>
    );
  if (files.data.content.length === 0)
    return (
      <EmptyState
        icon={Archive}
        title="Your archive is empty"
        description="Files you archive will be preserved, searchable and restorable."
        action={<Button render={<Link href="/archive" />}>Upload files</Button>}
      />
    );

  return (
    <div className="flex flex-col gap-2">
      <ul className="divide-border flex flex-col divide-y">
        {files.data.content.map((file) => (
          <FileRow key={file.id} file={file} />
        ))}
      </ul>
      <Button variant="ghost" size="sm" className="self-start" render={<Link href="/archive" />}>
        View all {files.data.totalElements} files
      </Button>
    </div>
  );
}
