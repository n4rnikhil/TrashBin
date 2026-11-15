import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/errors";
import type { FileDetail } from "@/lib/api/types";
import { UploadCanceledError } from "@/lib/api/upload";
import { UploadQueue, type Uploader } from "./upload-queue";

type Pending = {
  file: File;
  allowDuplicate: boolean;
  signal: AbortSignal;
  progress: (f: number) => void;
  resolve: (d: FileDetail) => void;
  reject: (e: unknown) => void;
};

/** An uploader whose calls the test resolves by hand. */
function controllableUploader() {
  const calls: Pending[] = [];
  const uploader: Uploader = (file, { allowDuplicate, signal, onProgress }) =>
    new Promise<FileDetail>((resolve, reject) => {
      signal.addEventListener("abort", () => reject(new UploadCanceledError()));
      calls.push({ file, allowDuplicate, signal, progress: onProgress, resolve, reject });
    });
  return { uploader, calls };
}

const file = (name: string) => new File(["x"], name);
const detail = (id: string) => ({ id, displayName: id }) as FileDetail;
const flush = () => new Promise((r) => setTimeout(r, 0));

describe("UploadQueue", () => {
  it("runs at most `concurrency` uploads and starts the next when one finishes", async () => {
    const { uploader, calls } = controllableUploader();
    const queue = new UploadQueue(uploader, { concurrency: 2 });

    queue.add([file("a"), file("b"), file("c")]);

    expect(calls).toHaveLength(2);
    expect(queue.getSnapshot().map((i) => i.status)).toEqual(["uploading", "uploading", "queued"]);

    calls[0]?.resolve(detail("a"));
    await flush();

    expect(calls).toHaveLength(3);
    expect(queue.getSnapshot()[0]?.status).toBe("done");
  });

  it("reports progress and notifies on success", async () => {
    const { uploader, calls } = controllableUploader();
    const onUploaded = vi.fn();
    const queue = new UploadQueue(uploader, { onUploaded });

    queue.add([file("a")]);
    calls[0]?.progress(0.5);
    expect(queue.getSnapshot()[0]?.progress).toBe(0.5);

    calls[0]?.resolve(detail("a"));
    await flush();

    expect(onUploaded).toHaveBeenCalledWith(detail("a"));
    expect(queue.getSnapshot()[0]).toMatchObject({ status: "done", progress: 1 });
  });

  it("turns a duplicate response into a choice, and 'keep both' retries with allowDuplicate", async () => {
    const { uploader, calls } = controllableUploader();
    const queue = new UploadQueue(uploader);

    queue.add([file("a")]);
    calls[0]?.reject(
      new ApiError(409, "DUPLICATE_FILE", "dup", undefined, [], {
        existing: { id: "f1", displayName: "Old name" },
      }),
    );
    await flush();

    expect(queue.getSnapshot()[0]).toMatchObject({
      status: "duplicate",
      existing: { id: "f1", displayName: "Old name" },
    });

    const id = queue.getSnapshot()[0]?.id ?? "";
    queue.keepBoth(id);
    expect(calls[1]?.allowDuplicate).toBe(true);
  });

  it("cancels running and queued uploads", async () => {
    const { uploader, calls } = controllableUploader();
    const queue = new UploadQueue(uploader, { concurrency: 1 });
    queue.add([file("a"), file("b")]);
    const [first, second] = queue.getSnapshot();

    queue.cancel(second?.id ?? "");
    queue.cancel(first?.id ?? "");
    await flush();

    expect(calls[0]?.signal.aborted).toBe(true);
    expect(queue.getSnapshot().map((i) => i.status)).toEqual(["canceled", "canceled"]);
    expect(calls).toHaveLength(1);
  });

  it("shows the server's message for other failures and lets finished items be cleared", async () => {
    const { uploader, calls } = controllableUploader();
    const queue = new UploadQueue(uploader);

    queue.add([file("a")]);
    calls[0]?.reject(new ApiError(413, "QUOTA_EXCEEDED", "This upload would exceed your quota."));
    await flush();

    expect(queue.getSnapshot()[0]).toMatchObject({
      status: "error",
      error: "This upload would exceed your quota.",
    });
    queue.clearFinished();
    expect(queue.getSnapshot()).toHaveLength(0);
  });
});
