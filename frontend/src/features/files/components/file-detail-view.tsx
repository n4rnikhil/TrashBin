"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, FileQuestion, Pencil } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import type { ReactNode } from "react";
import { EmptyState } from "@/components/patterns/empty-state";
import { CardSkeleton } from "@/components/patterns/skeletons";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Heading, Text } from "@/components/ui/typography";
import { ApiError, errorMessage } from "@/lib/api/errors";
import { fileKeys, filesApi } from "../api/files-api";
import { formatBytes, formatCalendarDate, formatDateTime } from "../format";
import { DownloadButton } from "./download-button";
import { EditFileDialog } from "./edit-file-dialog";
import { FavoriteButton } from "./favorite-button";
import { FileCollectionsCard } from "./file-collections-card";
import { FilePreview } from "./file-preview";
import { FileTagsCard } from "./file-tags-card";
import { FileTypeIcon, TYPE_LABELS } from "./file-type-icon";

function BackLink() {
  return (
    <Button
      variant="ghost"
      size="sm"
      className="-ml-2 self-start"
      render={<Link href="/archive" />}
    >
      <ArrowLeft aria-hidden /> Archive
    </Button>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-1 py-3 sm:grid-cols-[12rem_1fr] sm:gap-4">
      <dt className="text-muted-foreground text-sm">{label}</dt>
      <dd className="min-w-0 text-sm">{children}</dd>
    </div>
  );
}

/** One file: download, preview, metadata, notes, tags, collections and favorite. */
export function FileDetailView() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const [editing, setEditing] = useState(false);
  const file = useQuery({
    queryKey: fileKeys.detail(id),
    queryFn: ({ signal }) => filesApi.get(id, signal),
  });

  if (file.isPending)
    return (
      <div className="flex flex-col gap-6">
        <BackLink />
        <CardSkeleton />
      </div>
    );

  if (file.isError) {
    const missing =
      file.error instanceof ApiError && (file.error.status === 404 || file.error.status === 400);
    return (
      <div className="flex flex-col gap-6">
        <BackLink />
        <Card>
          <EmptyState
            icon={FileQuestion}
            title={missing ? "File not found" : "Could not load this file"}
            description={
              missing ? "It may have been purged, or the link is wrong." : errorMessage(file.error)
            }
          />
        </Card>
      </div>
    );
  }

  const f = file.data;
  return (
    <div className="flex flex-col gap-6">
      <BackLink />
      <header className="flex flex-wrap items-start gap-4">
        <FileTypeIcon typeGroup={f.typeGroup} size="lg" />
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          <Heading level={1} variant="title" className="break-words">
            {f.displayName}
          </Heading>
          <div className="flex flex-wrap gap-2">
            <Badge variant={f.status === "ARCHIVED" ? "success" : "warning"}>
              {f.status === "ARCHIVED" ? "Archived" : "Discarded"}
            </Badge>
            <Badge variant="secondary">{TYPE_LABELS[f.typeGroup]}</Badge>
            <Badge variant="outline">{formatBytes(f.sizeBytes)}</Badge>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <DownloadButton fileId={f.id} />
          <FavoriteButton fileId={f.id} favorite={f.favorite} />
          <Button variant="outline" onClick={() => setEditing(true)}>
            <Pencil aria-hidden /> Edit details
          </Button>
        </div>
      </header>
      <EditFileDialog file={f} open={editing} onOpenChange={setEditing} />

      {f.previewable ? <FilePreview file={f} /> : null}

      <FileTagsCard file={f} />
      <FileCollectionsCard file={f} />

      {f.contextNote || f.notes ? (
        <Card>
          <CardHeader>
            <CardTitle>Your notes</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            {f.contextNote ? (
              <div className="flex flex-col gap-1">
                <Text variant="overline" tone="subtle">
                  Why you kept it
                </Text>
                <Text className="whitespace-pre-wrap">{f.contextNote}</Text>
              </div>
            ) : null}
            {f.notes ? (
              <div className="flex flex-col gap-1">
                <Text variant="overline" tone="subtle">
                  Notes
                </Text>
                <Text className="whitespace-pre-wrap">{f.notes}</Text>
              </div>
            ) : null}
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>Details</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="divide-border divide-y">
            <Row label="Original file name">
              <span className="break-all">{f.originalFilename}</span>
            </Row>
            <Row label="Detected type">{f.mediaType}</Row>
            <Row label="Size">
              {formatBytes(f.sizeBytes)} ({f.sizeBytes.toLocaleString()} bytes)
            </Row>
            <Row label="Archived">{formatDateTime(f.archivedAt)}</Row>
            {f.originDate ? (
              <Row label="Origin date">{formatCalendarDate(f.originDate)}</Row>
            ) : null}
            <Row label="SHA-256">
              <code className="text-muted-foreground font-mono text-xs break-all">{f.sha256}</code>
            </Row>
          </dl>
        </CardContent>
      </Card>
    </div>
  );
}
