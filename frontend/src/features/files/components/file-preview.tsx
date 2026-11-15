"use client";

import { useQuery } from "@tanstack/react-query";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/typography";
import { errorMessage } from "@/lib/api/errors";
import type { FileDetail } from "@/lib/api/types";
import { fileKeys, filesApi } from "../api/files-api";

const MAX_TEXT_CHARS = 200_000;

type Kind = "image" | "pdf" | "text";

/** Mirrors the server's preview allowlist; the server decides, this only picks the viewer. */
export function previewKind(mediaType: string): Kind {
  if (mediaType.startsWith("image/")) return "image";
  if (mediaType === "application/pdf") return "pdf";
  return "text";
}

/**
 * Inline preview for allowlisted types. Images and PDFs load straight from a short-lived link;
 * text is fetched and shown as plain text, so it can never be interpreted as HTML.
 */
export function FilePreview({ file }: { file: FileDetail }) {
  const kind = previewKind(file.mediaType);
  const ticket = useQuery({
    queryKey: fileKeys.preview(file.id),
    queryFn: () => filesApi.createTicket(file.id, "PREVIEW"),
    // A link is valid for minutes; keep it for this visit instead of re-requesting on focus.
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Preview</CardTitle>
      </CardHeader>
      <CardContent>
        {ticket.isPending ? (
          <Skeleton className="h-64 w-full rounded-lg" />
        ) : ticket.isError ? (
          <Text tone="muted">Preview unavailable: {errorMessage(ticket.error)}</Text>
        ) : kind === "image" ? (
          <div className="bg-muted/50 flex justify-center rounded-lg p-2">
            {/* eslint-disable-next-line @next/next/no-img-element -- signed, uncacheable URL */}
            <img
              src={ticket.data.url}
              alt={`Preview of ${file.displayName}`}
              className="max-h-[70vh] max-w-full rounded object-contain"
            />
          </div>
        ) : kind === "pdf" ? (
          <iframe
            src={ticket.data.url}
            title={`Preview of ${file.displayName}`}
            className="h-[75vh] w-full rounded-lg border"
          />
        ) : (
          <TextPreview url={ticket.data.url} fileId={file.id} />
        )}
      </CardContent>
    </Card>
  );
}

function TextPreview({ url, fileId }: { url: string; fileId: string }) {
  const text = useQuery({
    queryKey: [...fileKeys.preview(fileId), "text", url],
    queryFn: ({ signal }) => filesApi.fetchText(url, MAX_TEXT_CHARS, signal),
    staleTime: Infinity,
    gcTime: 0,
  });

  if (text.isPending) return <Skeleton className="h-40 w-full rounded-lg" />;
  if (text.isError) return <Text tone="muted">Preview unavailable.</Text>;
  return (
    <div className="flex flex-col gap-2">
      <pre className="bg-muted/50 max-h-[70vh] overflow-auto rounded-lg p-4 font-mono text-xs leading-relaxed break-words whitespace-pre-wrap">
        {text.data.text}
      </pre>
      {text.data.truncated ? (
        <Text variant="caption" tone="muted" className="font-normal">
          Showing the first {MAX_TEXT_CHARS.toLocaleString()} characters. Download the file to see
          everything.
        </Text>
      ) : null}
    </div>
  );
}
