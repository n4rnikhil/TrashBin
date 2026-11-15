"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarHeart, Hourglass, Shuffle } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { EmptyState } from "@/components/patterns/empty-state";
import { ListSkeleton } from "@/components/patterns/skeletons";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { errorMessage } from "@/lib/api/errors";
import type { RediscoverMode } from "@/lib/api/types";
import { fileKeys, filesApi } from "../api/files-api";
import { FileRow } from "./file-list";

const SECTIONS: readonly {
  mode: RediscoverMode;
  title: string;
  description: string;
  empty: string;
  icon: LucideIcon;
}[] = [
  {
    mode: "on-this-day",
    title: "On this day",
    description: "Archived — or from — today’s date in earlier years.",
    empty: "Nothing from this date in past years yet.",
    icon: CalendarHeart,
  },
  {
    mode: "forgotten",
    title: "Forgotten",
    description: "Kept for over six months and not opened since.",
    empty: "Nothing forgotten — you have looked at everything recently.",
    icon: Hourglass,
  },
];

/** Gentle ways back into the archive (FR-M1 – FR-M3). Plain queries, no AI. */
export function RediscoverView() {
  return (
    <div className="flex flex-col gap-6">
      <SurpriseCard />
      {SECTIONS.map((section) => (
        <Section key={section.mode} {...section} />
      ))}
    </div>
  );
}

function Section({ mode, title, description, empty, icon }: (typeof SECTIONS)[number]) {
  const files = useQuery({
    queryKey: fileKeys.rediscover(mode),
    queryFn: ({ signal }) => filesApi.rediscover(mode, signal),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent>
        {files.isPending ? (
          <ListSkeleton rows={2} />
        ) : files.isError ? (
          <div role="alert" className="text-destructive text-sm">
            {errorMessage(files.error)}
          </div>
        ) : files.data.length === 0 ? (
          <EmptyState size="compact" icon={icon} title={title} description={empty} />
        ) : (
          <ul className="divide-border flex flex-col divide-y">
            {files.data.map((file) => (
              <FileRow key={file.id} file={file} />
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

function SurpriseCard() {
  const queryClient = useQueryClient();
  const pick = useQuery({
    queryKey: fileKeys.rediscover("random"),
    queryFn: ({ signal }) => filesApi.rediscover("random", signal),
  });

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-4">
        <div className="flex flex-col gap-1">
          <CardTitle>Surprise me</CardTitle>
          <CardDescription>A random file from your archive.</CardDescription>
        </div>
        <Button
          variant="outline"
          size="sm"
          disabled={pick.isFetching}
          onClick={() => queryClient.invalidateQueries({ queryKey: fileKeys.rediscover("random") })}
        >
          <Shuffle aria-hidden /> Another one
        </Button>
      </CardHeader>
      <CardContent>
        {pick.isPending ? (
          <ListSkeleton rows={1} />
        ) : pick.isError ? (
          <div role="alert" className="text-destructive text-sm">
            {errorMessage(pick.error)}
          </div>
        ) : pick.data[0] ? (
          <ul>
            <FileRow file={pick.data[0]} />
          </ul>
        ) : (
          <EmptyState
            size="compact"
            icon={Shuffle}
            title="Nothing to pick from"
            description="Archive a few files first."
          />
        )}
      </CardContent>
    </Card>
  );
}
