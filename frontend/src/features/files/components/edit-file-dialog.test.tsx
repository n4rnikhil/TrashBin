import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/errors";
import type { FileDetail } from "@/lib/api/types";
import { filesApi } from "../api/files-api";
import { EditFileDialog } from "./edit-file-dialog";

const file: FileDetail = {
  id: "f1",
  displayName: "lease.pdf",
  originalFilename: "lease.pdf",
  mediaType: "application/pdf",
  typeGroup: "DOCUMENT",
  sizeBytes: 10,
  sha256: "0".repeat(64),
  status: "ARCHIVED",
  favorite: false,
  notes: "old notes",
  contextNote: null,
  originDate: null,
  tags: [],
  collections: [],
  archivedAt: "2026-11-15T10:00:00Z",
  lastAccessedAt: null,
  discardedAt: null,
  purgeAfter: null,
  updatedAt: "2026-11-15T10:00:00Z",
  version: 4,
};

function renderDialog(onOpenChange = vi.fn()) {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <EditFileDialog file={file} open onOpenChange={onOpenChange} />
    </QueryClientProvider>,
  );
  return onOpenChange;
}

describe("EditFileDialog", () => {
  afterEach(() => vi.restoreAllMocks());

  it("sends every field with the version the user is looking at", async () => {
    const update = vi.spyOn(filesApi, "update").mockResolvedValue({ ...file, version: 5 });
    const onOpenChange = renderDialog();

    const name = screen.getByLabelText("Name");
    await userEvent.clear(name);
    await userEvent.type(name, "Apartment lease");
    await userEvent.clear(screen.getByLabelText("Notes"));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(update).toHaveBeenCalledWith("f1", 4, {
      displayName: "Apartment lease",
      contextNote: "",
      notes: "",
      originDate: "",
    });
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("offers a reload instead of overwriting when the file changed elsewhere", async () => {
    vi.spyOn(filesApi, "update").mockRejectedValue(
      new ApiError(412, "VERSION_CONFLICT", "changed"),
    );
    const onOpenChange = renderDialog();

    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/changed somewhere else/);
    expect(screen.getByRole("button", { name: "Reload latest version" })).toBeInTheDocument();
    expect(onOpenChange).not.toHaveBeenCalledWith(false);
  });

  it("validates before sending", async () => {
    const update = vi.spyOn(filesApi, "update");
    renderDialog();

    await userEvent.clear(screen.getByLabelText("Name"));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Enter a name.")).toBeInTheDocument();
    expect(update).not.toHaveBeenCalled();
  });
});
