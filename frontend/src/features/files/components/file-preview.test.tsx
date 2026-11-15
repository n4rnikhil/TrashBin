import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { FileDetail } from "@/lib/api/types";
import { filesApi } from "../api/files-api";
import * as download from "../download";
import { DownloadButton } from "./download-button";
import { FilePreview, previewKind } from "./file-preview";

const base: FileDetail = {
  id: "f1",
  displayName: "Holiday",
  originalFilename: "holiday.png",
  mediaType: "image/png",
  typeGroup: "IMAGE",
  sizeBytes: 10,
  sha256: "0".repeat(64),
  status: "ARCHIVED",
  favorite: false,
  previewable: true,
  notes: null,
  contextNote: null,
  originDate: null,
  tags: [],
  collections: [],
  archivedAt: "2026-11-15T10:00:00Z",
  lastAccessedAt: null,
  discardedAt: null,
  purgeAfter: null,
  updatedAt: "2026-11-15T10:00:00Z",
  version: 0,
};

const ticket = { url: "/api/v1/content/abc.def", expiresAt: "2026-11-15T10:05:00Z" };

function wrap(ui: ReactNode) {
  return render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      {ui}
    </QueryClientProvider>,
  );
}

describe("previews and downloads", () => {
  afterEach(() => vi.restoreAllMocks());

  it("picks the viewer from the media type", () => {
    expect(previewKind("image/webp")).toBe("image");
    expect(previewKind("application/pdf")).toBe("pdf");
    expect(previewKind("text/csv")).toBe("text");
  });

  it("shows images from a short-lived preview link", async () => {
    const create = vi.spyOn(filesApi, "createTicket").mockResolvedValue(ticket);

    wrap(<FilePreview file={base} />);

    const img = await screen.findByRole("img", { name: "Preview of Holiday" });
    expect(img).toHaveAttribute("src", ticket.url);
    expect(create).toHaveBeenCalledWith("f1", "PREVIEW");
  });

  it("renders text as text, never as markup", async () => {
    vi.spyOn(filesApi, "createTicket").mockResolvedValue(ticket);
    vi.spyOn(filesApi, "fetchText").mockResolvedValue({
      text: "<script>alert(1)</script>",
      truncated: false,
    });

    const { container } = wrap(
      <FilePreview file={{ ...base, mediaType: "text/plain", typeGroup: "TEXT" }} />,
    );

    expect(await screen.findByText("<script>alert(1)</script>")).toBeInTheDocument();
    expect(container.querySelector("script")).toBeNull();
  });

  it("frames PDFs from the same-origin link", async () => {
    vi.spyOn(filesApi, "createTicket").mockResolvedValue(ticket);

    wrap(<FilePreview file={{ ...base, mediaType: "application/pdf", typeGroup: "DOCUMENT" }} />);

    expect(await screen.findByTitle("Preview of Holiday")).toHaveAttribute("src", ticket.url);
  });

  it("downloads through a fresh download link", async () => {
    vi.spyOn(filesApi, "createTicket").mockResolvedValue(ticket);
    const start = vi.spyOn(download, "startDownload").mockImplementation(() => undefined);

    wrap(<DownloadButton fileId="f1" />);
    await userEvent.click(screen.getByRole("button", { name: "Download" }));

    await vi.waitFor(() => expect(start).toHaveBeenCalledWith(ticket.url));
    expect(filesApi.createTicket).toHaveBeenCalledWith("f1", "DOWNLOAD");
  });
});
