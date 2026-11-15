import { ApiError, errorMessage } from "@/lib/api/errors";
import type { FileDetail, FileSummary } from "@/lib/api/types";
import { UploadCanceledError } from "@/lib/api/upload";

export type UploadStatus = "queued" | "uploading" | "done" | "duplicate" | "error" | "canceled";

export type UploadItem = {
  id: string;
  file: File;
  status: UploadStatus;
  /** 0..1 while uploading. */
  progress: number;
  allowDuplicate: boolean;
  result?: FileDetail;
  /** The already archived file, when the server reported a duplicate. */
  existing?: Pick<FileSummary, "id" | "displayName">;
  error?: string;
};

export type Uploader = (
  file: File,
  options: { allowDuplicate: boolean; signal: AbortSignal; onProgress: (fraction: number) => void },
) => Promise<FileDetail>;

type Listener = () => void;

/**
 * Framework-independent upload queue: runs a bounded number of uploads at a time, tracks progress,
 * handles cancellation and the server's duplicate response. React subscribes through
 * useSyncExternalStore; the logic is testable without rendering.
 */
export class UploadQueue {
  private items: readonly UploadItem[] = [];
  private readonly controllers = new Map<string, AbortController>();
  private readonly listeners = new Set<Listener>();
  private nextId = 0;

  constructor(
    private readonly uploader: Uploader,
    private readonly options: {
      concurrency?: number;
      onUploaded?: (file: FileDetail) => void;
    } = {},
  ) {}

  subscribe = (listener: Listener): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  getSnapshot = (): readonly UploadItem[] => this.items;

  add(files: Iterable<File>): void {
    const added = Array.from(files, (file) => ({
      id: `upload-${++this.nextId}`,
      file,
      status: "queued" as const,
      progress: 0,
      allowDuplicate: false,
    }));
    if (added.length === 0) return;
    this.set([...this.items, ...added]);
    this.pump();
  }

  cancel(id: string): void {
    const item = this.find(id);
    if (item?.status === "queued") this.update(id, { status: "canceled" });
    this.controllers.get(id)?.abort();
  }

  /** Upload a reported duplicate anyway, as a separate copy. */
  keepBoth(id: string): void {
    if (this.find(id)?.status !== "duplicate") return;
    this.update(id, { status: "queued", progress: 0, allowDuplicate: true, existing: undefined });
    this.pump();
  }

  dismiss(id: string): void {
    const item = this.find(id);
    if (!item || item.status === "uploading" || item.status === "queued") return;
    this.set(this.items.filter((i) => i.id !== id));
  }

  clearFinished(): void {
    this.set(this.items.filter((i) => i.status === "uploading" || i.status === "queued"));
  }

  private pump(): void {
    const limit = this.options.concurrency ?? 2;
    let running = this.items.filter((i) => i.status === "uploading").length;
    for (const item of this.items) {
      if (running >= limit) break;
      if (item.status === "queued") {
        running++;
        void this.run(item);
      }
    }
  }

  private async run(item: UploadItem): Promise<void> {
    const controller = new AbortController();
    this.controllers.set(item.id, controller);
    this.update(item.id, { status: "uploading", progress: 0, error: undefined });
    try {
      const result = await this.uploader(item.file, {
        allowDuplicate: item.allowDuplicate,
        signal: controller.signal,
        onProgress: (fraction) => this.update(item.id, { progress: fraction }),
      });
      this.update(item.id, { status: "done", progress: 1, result });
      this.options.onUploaded?.(result);
    } catch (error) {
      this.update(item.id, outcomeOf(error));
    } finally {
      this.controllers.delete(item.id);
      this.pump();
    }
  }

  private find(id: string): UploadItem | undefined {
    return this.items.find((i) => i.id === id);
  }

  private update(id: string, patch: Partial<UploadItem>): void {
    this.set(this.items.map((i) => (i.id === id ? { ...i, ...patch } : i)));
  }

  private set(items: readonly UploadItem[]): void {
    this.items = items;
    this.listeners.forEach((listener) => listener());
  }
}

function outcomeOf(error: unknown): Partial<UploadItem> {
  if (error instanceof UploadCanceledError) return { status: "canceled" };
  if (error instanceof ApiError && error.code === "DUPLICATE_FILE") {
    const existing = error.problem["existing"];
    if (typeof existing === "object" && existing !== null) {
      const record = existing as Record<string, unknown>;
      if (typeof record["id"] === "string" && typeof record["displayName"] === "string") {
        return {
          status: "duplicate",
          existing: { id: record["id"], displayName: record["displayName"] },
        };
      }
    }
  }
  return { status: "error", error: errorMessage(error) };
}
