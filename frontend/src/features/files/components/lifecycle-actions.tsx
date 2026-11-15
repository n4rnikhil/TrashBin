"use client";

import { ArchiveRestore, Trash2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Text } from "@/components/ui/typography";
import { errorMessage } from "@/lib/api/errors";
import type { FileDetail } from "@/lib/api/types";
import { formatDate } from "../format";
import { useLifecycle } from "../hooks/use-lifecycle";

/** Whole days until `iso`, never negative. */
export function daysUntil(iso: string, now: Date = new Date()): number {
  return Math.max(0, Math.ceil((new Date(iso).getTime() - now.getTime()) / 86_400_000));
}

/** "Discard" for archived files: a reversible step, so a simple confirmation is enough. */
export function DiscardButton({ file }: { file: FileDetail }) {
  const [open, setOpen] = useState(false);
  const { discard } = useLifecycle(file.id);

  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)}>
        <Trash2 aria-hidden /> Discard
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Discard “{file.displayName}”?</DialogTitle>
            <DialogDescription>
              It moves to Discarded. You can restore it from there until it is deleted for good.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              Keep it
            </Button>
            <Button
              disabled={discard.isPending}
              onClick={() =>
                discard.mutate(undefined, {
                  onSuccess: () => {
                    setOpen(false);
                    toast.success("File discarded", {
                      description: "Find it under Discarded if you change your mind.",
                    });
                  },
                  onError: (error) => toast.error(errorMessage(error)),
                })
              }
            >
              Discard
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

/** Shown on a discarded file: when it will be deleted, and the ways out. */
export function DiscardedBanner({ file }: { file: FileDetail }) {
  const router = useRouter();
  const [confirming, setConfirming] = useState(false);
  const { restore, purge } = useLifecycle(file.id);

  const deadline = file.purgeAfter
    ? `It will be deleted permanently on ${formatDate(file.purgeAfter)} (in ${daysUntil(
        file.purgeAfter,
      )} days).`
    : "It stays here until you delete it permanently.";

  return (
    <div
      role="status"
      className="border-warning/30 bg-warning/10 flex flex-col gap-3 rounded-xl border p-4 sm:flex-row sm:items-center sm:justify-between"
    >
      <div className="flex flex-col gap-0.5">
        <Text className="font-medium">
          Discarded {file.discardedAt ? `on ${formatDate(file.discardedAt)}` : ""}
        </Text>
        <Text tone="muted">{deadline}</Text>
      </div>
      <div className="flex shrink-0 flex-wrap gap-2">
        <Button
          disabled={restore.isPending}
          onClick={() =>
            restore.mutate(undefined, {
              onSuccess: () => toast.success("Restored to your archive"),
              onError: (error) => toast.error(errorMessage(error)),
            })
          }
        >
          <ArchiveRestore aria-hidden /> Restore
        </Button>
        <Button variant="destructive" onClick={() => setConfirming(true)}>
          Delete permanently
        </Button>
      </div>

      <Dialog open={confirming} onOpenChange={setConfirming}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete “{file.displayName}” permanently?</DialogTitle>
            <DialogDescription>
              The file, its notes, tags, collection links and history are removed for good. This
              cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirming(false)}>
              Keep it
            </Button>
            <Button
              variant="destructive"
              disabled={purge.isPending}
              onClick={() =>
                purge.mutate(undefined, {
                  onSuccess: () => {
                    toast.success("Deleted permanently");
                    router.push("/discarded");
                  },
                  onError: (error) => toast.error(errorMessage(error)),
                })
              }
            >
              Delete permanently
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
