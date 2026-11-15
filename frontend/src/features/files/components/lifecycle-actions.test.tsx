import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { FileDetail } from "@/lib/api/types";
import { filesApi } from "../api/files-api";
import { describeEvent } from "./activity-timeline";
import { DiscardButton, DiscardedBanner, daysUntil } from "./lifecycle-actions";
import { StorageMeter } from "./stats-card";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const file: FileDetail = {
  id: "f1",
  displayName: "Old lease",
  originalFilename: "lease.pdf",
  mediaType: "application/pdf",
  typeGroup: "DOCUMENT",
  sizeBytes: 10,
  sha256: "0".repeat(64),
  status: "ARCHIVED",
  favorite: false,
  extractionStatus: "DONE",
  previewable: true,
  notes: null,
  contextNote: null,
  originDate: null,
  tags: [],
  collections: [],
  archivedAt: "2026-11-01T10:00:00Z",
  lastAccessedAt: null,
  discardedAt: null,
  purgeAfter: null,
  updatedAt: "2026-11-01T10:00:00Z",
  version: 0,
};

function wrap(ui: ReactNode) {
  return render(<QueryClientProvider client={new QueryClient()}>{ui}</QueryClientProvider>);
}

describe("lifecycle actions", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    push.mockReset();
  });

  it("discards only after confirmation", async () => {
    const discard = vi
      .spyOn(filesApi, "discard")
      .mockResolvedValue({ ...file, status: "DISCARDED" });
    wrap(<DiscardButton file={file} />);

    await userEvent.click(screen.getByRole("button", { name: "Discard" }));
    expect(discard).not.toHaveBeenCalled();

    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "Discard" }));
    await vi.waitFor(() => expect(discard).toHaveBeenCalledWith("f1"));
  });

  it("shows the purge date and offers restore", async () => {
    const restore = vi.spyOn(filesApi, "restore").mockResolvedValue(file);
    wrap(
      <DiscardedBanner
        file={{
          ...file,
          status: "DISCARDED",
          discardedAt: "2026-11-01T10:00:00Z",
          purgeAfter: "2099-12-01T10:00:00Z",
        }}
      />,
    );

    expect(screen.getByText(/will be deleted permanently on/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Restore" }));
    await vi.waitFor(() => expect(restore).toHaveBeenCalledWith("f1"));
  });

  it("deletes permanently only after a second, explicit confirmation", async () => {
    const purge = vi.spyOn(filesApi, "purge").mockResolvedValue(undefined);
    wrap(<DiscardedBanner file={{ ...file, status: "DISCARDED" }} />);

    expect(screen.getByText(/stays here until you delete it/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Delete permanently" }));
    expect(purge).not.toHaveBeenCalled();

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/cannot be undone/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Delete permanently" }));

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith("/discarded"));
    expect(purge).toHaveBeenCalledWith("f1");
  });

  it("formats countdowns, timeline entries and storage use", () => {
    const now = new Date("2026-11-16T00:00:00Z");
    expect(daysUntil("2026-11-26T00:00:00Z", now)).toBe(10);
    expect(daysUntil("2026-11-01T00:00:00Z", now)).toBe(0);
    expect(
      describeEvent({
        id: "e",
        type: "METADATA_EDITED",
        occurredAt: "2026-11-16T00:00:00Z",
        details: { fields: ["notes", "displayName"] },
      }),
    ).toBe("Details edited: notes, displayName");

    render(<StorageMeter used={950} quota={1000} />);
    expect(screen.getByRole("progressbar", { name: "Storage used" })).toHaveAttribute(
      "aria-valuenow",
      "95",
    );
  });
});
