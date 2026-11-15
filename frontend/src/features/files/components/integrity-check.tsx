"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ShieldAlert, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { errorMessage } from "@/lib/api/errors";
import { fileKeys, filesApi } from "../api/files-api";

/** Re-hashes the stored bytes and compares them with the checksum taken at upload (FR-F6). */
export function IntegrityCheck({ fileId }: { fileId: string }) {
  const queryClient = useQueryClient();
  const verify = useMutation({
    mutationFn: () => filesApi.verify(fileId),
    onSettled: () => queryClient.invalidateQueries({ queryKey: fileKeys.events(fileId) }),
  });

  return (
    <div className="flex flex-wrap items-center gap-3">
      <Button
        variant="outline"
        size="sm"
        disabled={verify.isPending}
        onClick={() => verify.mutate()}
      >
        <ShieldCheck aria-hidden /> Verify integrity
      </Button>
      <span aria-live="polite" className="text-sm">
        {verify.isPending ? (
          <span className="text-muted-foreground">Checking every byte…</span>
        ) : verify.isError ? (
          <span className="text-destructive">{errorMessage(verify.error)}</span>
        ) : verify.data ? (
          verify.data.ok ? (
            <span className="text-success inline-flex items-center gap-1">
              <ShieldCheck className="size-4" aria-hidden /> Intact — matches the original checksum
            </span>
          ) : (
            <span className="text-destructive inline-flex items-center gap-1">
              <ShieldAlert className="size-4" aria-hidden />
              {verify.data.actualSha256
                ? "Changed on disk — restore it from a backup"
                : "The stored file is missing — restore it from a backup"}
            </span>
          )
        ) : null}
      </span>
    </div>
  );
}
