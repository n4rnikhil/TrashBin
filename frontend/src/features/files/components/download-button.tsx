"use client";

import { useMutation } from "@tanstack/react-query";
import { Download } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { errorMessage } from "@/lib/api/errors";
import { filesApi } from "../api/files-api";
import { startDownload } from "../download";

/** Downloads the original, unchanged file through a short-lived link. */
export function DownloadButton({ fileId }: { fileId: string }) {
  const download = useMutation({
    mutationFn: () => filesApi.createTicket(fileId, "DOWNLOAD"),
    onSuccess: (ticket) => startDownload(ticket.url),
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Button disabled={download.isPending} onClick={() => download.mutate()}>
      <Download aria-hidden />
      Download
    </Button>
  );
}
