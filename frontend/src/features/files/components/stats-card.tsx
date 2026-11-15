"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/typography";
import { errorMessage } from "@/lib/api/errors";
import { fileKeys, filesApi } from "../api/files-api";
import { formatBytes } from "../format";

/** Real counts and storage use (FR-M5). */
export function StatsCard() {
  const stats = useQuery({
    queryKey: fileKeys.stats,
    queryFn: ({ signal }) => filesApi.stats(signal),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Your archive</CardTitle>
      </CardHeader>
      <CardContent>
        {stats.isPending ? (
          <Skeleton className="h-20 w-full" />
        ) : stats.isError ? (
          <Text tone="muted">{errorMessage(stats.error)}</Text>
        ) : (
          <div className="flex flex-col gap-4">
            <dl className="grid grid-cols-2 gap-3">
              <Stat label="Archived" value={stats.data.archivedCount} href="/archive" />
              <Stat label="Discarded" value={stats.data.discardedCount} href="/discarded" />
            </dl>
            <StorageMeter used={stats.data.usedBytes} quota={stats.data.quotaBytes} />
          </div>
        )}
      </CardContent>
    </Card>
  );
}

/** A `<div>` group keeps the `<dl>` valid; the label link stretches over the whole tile. */
function Stat({ label, value, href }: { label: string; value: number; href: string }) {
  return (
    <div className="bg-secondary/50 hover:bg-secondary relative flex flex-col rounded-lg px-3 py-2 transition-colors">
      <dt className="text-muted-foreground text-xs">
        <Link href={href} className="after:absolute after:inset-0 after:rounded-lg">
          {label}
        </Link>
      </dt>
      <dd className="text-xl font-semibold tabular-nums">{value.toLocaleString()}</dd>
    </div>
  );
}

export function StorageMeter({ used, quota }: { used: number; quota: number }) {
  const percent = quota > 0 ? Math.min(100, Math.round((used / quota) * 100)) : 0;
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex justify-between text-xs">
        <span className="text-muted-foreground">Storage</span>
        <span className="tabular-nums">
          {formatBytes(used)} of {formatBytes(quota)}
        </span>
      </div>
      <div
        role="progressbar"
        aria-label="Storage used"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        className="bg-muted h-2 overflow-hidden rounded-full"
      >
        <div
          className={`h-full rounded-full ${percent >= 90 ? "bg-destructive" : "bg-primary"}`}
          style={{ width: `${percent}%` }}
        />
      </div>
    </div>
  );
}
