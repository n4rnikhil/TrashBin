"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Check, CircleAlert, CloudUpload, Copy, Loader2, X } from "lucide-react";
import Link from "next/link";
import { useRef, useState, useSyncExternalStore } from "react";
import type { DragEvent } from "react";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/typography";
import { cn } from "@/lib/utils";
import { fileKeys, filesApi } from "../api/files-api";
import { formatBytes } from "../format";
import { UploadQueue, type UploadItem } from "../upload-queue";

function useUploadQueue(): UploadQueue {
  const queryClient = useQueryClient();
  const [queue] = useState(
    () =>
      new UploadQueue(
        (file, { allowDuplicate, signal, onProgress }) =>
          filesApi.upload(file, { allowDuplicate }, { signal, onProgress }),
        { onUploaded: () => void queryClient.invalidateQueries({ queryKey: fileKeys.all }) },
      ),
  );
  return queue;
}

/** Drag-and-drop (or picker) upload with a live, per-file queue. */
export function UploadPanel() {
  const queue = useUploadQueue();
  const items = useSyncExternalStore(queue.subscribe, queue.getSnapshot, queue.getSnapshot);
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  const onDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setDragging(false);
    queue.add(event.dataTransfer.files);
  };

  const finished = items.some((i) => i.status !== "queued" && i.status !== "uploading");

  return (
    <div className="flex flex-col gap-4">
      <div
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={cn(
          "flex flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed px-6 py-10 text-center transition-colors",
          dragging ? "border-primary bg-accent" : "border-input bg-background",
        )}
      >
        <span className="bg-card text-primary ring-border flex size-12 items-center justify-center rounded-2xl shadow-sm ring-1">
          <CloudUpload className="size-6" strokeWidth={1.75} aria-hidden />
        </span>
        <div className="flex flex-col gap-1">
          <Text className="font-medium">Drop files here to archive them</Text>
          <Text variant="caption" tone="muted" className="font-normal">
            Originals are kept exactly as uploaded.
          </Text>
        </div>
        <Button onClick={() => inputRef.current?.click()}>Choose files</Button>
        <input
          ref={inputRef}
          type="file"
          multiple
          className="sr-only"
          tabIndex={-1}
          aria-hidden
          onChange={(event) => {
            if (event.target.files) queue.add(event.target.files);
            event.target.value = "";
          }}
        />
      </div>

      {items.length > 0 ? (
        <div className="flex flex-col gap-2">
          <div className="flex items-center justify-between">
            <Text variant="overline" tone="subtle">
              Uploads
            </Text>
            {finished ? (
              <Button variant="ghost" size="xs" onClick={() => queue.clearFinished()}>
                Clear finished
              </Button>
            ) : null}
          </div>
          <ul className="divide-border bg-card ring-border flex flex-col divide-y rounded-xl ring-1">
            {items.map((item) => (
              <UploadRow key={item.id} item={item} queue={queue} />
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  );
}

function UploadRow({ item, queue }: { item: UploadItem; queue: UploadQueue }) {
  const percent = Math.round(item.progress * 100);
  return (
    <li className="flex flex-col gap-2 px-4 py-3">
      <div className="flex items-center gap-3">
        <StatusIcon status={item.status} />
        <div className="flex min-w-0 flex-1 flex-col">
          <Text as="span" className="truncate font-medium">
            {item.file.name}
          </Text>
          <Text as="span" variant="caption" tone="muted" className="font-normal" aria-live="polite">
            {describe(item, percent)}
          </Text>
        </div>
        <RowActions item={item} queue={queue} />
      </div>
      {item.status === "uploading" ? (
        <div
          role="progressbar"
          aria-label={`Uploading ${item.file.name}`}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={percent}
          className="bg-muted h-1.5 overflow-hidden rounded-full"
        >
          <div
            className="bg-primary h-full rounded-full transition-[width] duration-200"
            style={{ width: `${percent}%` }}
          />
        </div>
      ) : null}
    </li>
  );
}

function describe(item: UploadItem, percent: number): string {
  const size = formatBytes(item.file.size);
  switch (item.status) {
    case "queued":
      return `${size} · Waiting`;
    case "uploading":
      return `${size} · ${percent}%`;
    case "done":
      return `${size} · Archived`;
    case "duplicate":
      return `Already archived as “${item.existing?.displayName ?? "another file"}”`;
    case "canceled":
      return `${size} · Canceled`;
    case "error":
      return item.error ?? "Upload failed";
  }
}

function StatusIcon({ status }: { status: UploadItem["status"] }) {
  const base = "flex size-8 shrink-0 items-center justify-center rounded-lg";
  if (status === "done")
    return (
      <span className={cn(base, "bg-success/10 text-success")} aria-hidden>
        <Check className="size-4" />
      </span>
    );
  if (status === "error")
    return (
      <span className={cn(base, "bg-destructive/10 text-destructive")} aria-hidden>
        <CircleAlert className="size-4" />
      </span>
    );
  if (status === "duplicate")
    return (
      <span className={cn(base, "bg-warning/10 text-warning")} aria-hidden>
        <Copy className="size-4" />
      </span>
    );
  return (
    <span className={cn(base, "bg-muted text-muted-foreground")} aria-hidden>
      <Loader2
        className={cn(
          "size-4",
          status === "uploading" || status === "queued" ? "animate-spin" : "",
        )}
      />
    </span>
  );
}

function RowActions({ item, queue }: { item: UploadItem; queue: UploadQueue }) {
  const name = item.file.name;
  switch (item.status) {
    case "queued":
    case "uploading":
      return (
        <Button variant="ghost" size="sm" onClick={() => queue.cancel(item.id)}>
          Cancel
        </Button>
      );
    case "duplicate":
      return (
        <div className="flex shrink-0 gap-1">
          <Button variant="outline" size="sm" onClick={() => queue.keepBoth(item.id)}>
            Keep both
          </Button>
          <Button variant="ghost" size="sm" onClick={() => queue.dismiss(item.id)}>
            Skip
          </Button>
        </div>
      );
    case "done":
      return (
        <div className="flex shrink-0 gap-1">
          {item.result ? (
            <Button variant="ghost" size="sm" render={<Link href={`/files/${item.result.id}`} />}>
              View
            </Button>
          ) : null}
          <DismissButton name={name} onClick={() => queue.dismiss(item.id)} />
        </div>
      );
    default:
      return <DismissButton name={name} onClick={() => queue.dismiss(item.id)} />;
  }
}

function DismissButton({ name, onClick }: { name: string; onClick: () => void }) {
  return (
    <Button variant="ghost" size="icon-sm" aria-label={`Dismiss ${name}`} onClick={onClick}>
      <X />
    </Button>
  );
}
