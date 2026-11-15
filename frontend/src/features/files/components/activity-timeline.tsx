"use client";

import { useQuery } from "@tanstack/react-query";
import {
  Archive,
  ArchiveRestore,
  Download,
  Eye,
  type LucideIcon,
  Pencil,
  ShieldCheck,
  Trash2,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/typography";
import { errorMessage } from "@/lib/api/errors";
import type { FileEvent, FileEventType } from "@/lib/api/types";
import { fileKeys, filesApi } from "../api/files-api";
import { formatDateTime } from "../format";

const EVENTS: Record<FileEventType, { label: string; icon: LucideIcon }> = {
  ARCHIVED: { label: "Archived", icon: Archive },
  METADATA_EDITED: { label: "Details edited", icon: Pencil },
  DOWNLOADED: { label: "Downloaded", icon: Download },
  PREVIEWED: { label: "Previewed", icon: Eye },
  DISCARDED: { label: "Discarded", icon: Trash2 },
  RESTORED: { label: "Restored", icon: ArchiveRestore },
  INTEGRITY_VERIFIED: { label: "Integrity checked", icon: ShieldCheck },
};

export function describeEvent(event: FileEvent): string {
  const base = EVENTS[event.type]?.label ?? event.type;
  const fields = event.details["fields"];
  if (event.type === "METADATA_EDITED" && Array.isArray(fields) && fields.length > 0) {
    return `${base}: ${fields.join(", ")}`;
  }
  if (event.type === "INTEGRITY_VERIFIED") {
    return event.details["ok"] === false ? `${base}: problem found` : `${base}: intact`;
  }
  return base;
}

/** Everything that happened to a file, newest first (FR-M4). */
export function ActivityTimeline({ fileId }: { fileId: string }) {
  const events = useQuery({
    queryKey: fileKeys.events(fileId),
    queryFn: ({ signal }) => filesApi.events(fileId, signal),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Activity</CardTitle>
      </CardHeader>
      <CardContent>
        {events.isPending ? (
          <Skeleton className="h-24 w-full" />
        ) : events.isError ? (
          <Text tone="muted">{errorMessage(events.error)}</Text>
        ) : (
          <ol className="border-border relative ml-3 flex flex-col gap-4 border-l pl-6">
            {events.data.content.map((event) => {
              const Icon = EVENTS[event.type]?.icon ?? Archive;
              return (
                <li key={event.id} className="relative">
                  <span className="bg-card text-muted-foreground ring-border absolute top-0.5 -left-[2.1rem] flex size-6 items-center justify-center rounded-full ring-1">
                    <Icon className="size-3.5" aria-hidden />
                  </span>
                  <Text as="span" className="block font-medium">
                    {describeEvent(event)}
                  </Text>
                  <Text as="span" variant="caption" tone="muted" className="font-normal">
                    {formatDateTime(event.occurredAt)}
                  </Text>
                </li>
              );
            })}
          </ol>
        )}
      </CardContent>
    </Card>
  );
}
