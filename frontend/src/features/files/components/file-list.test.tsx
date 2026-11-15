import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as client from "@/lib/api/client";
import type { FileSummary, PageResponse } from "@/lib/api/types";
import { filesApi } from "../api/files-api";
import { FileList } from "./file-list";

const push = vi.fn();
let search = "";
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  usePathname: () => "/archive",
  useSearchParams: () => new URLSearchParams(search),
}));

const TAG = "0b6c2f6e-2f5c-4c52-9a1e-1b8f2a1c9d10";

const page = (content: FileSummary[], totalPages = 1): PageResponse<FileSummary> => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages,
});

const summary: FileSummary = {
  id: "f1",
  displayName: "Lease 2021.pdf",
  mediaType: "application/pdf",
  typeGroup: "DOCUMENT",
  sizeBytes: 1536,
  status: "ARCHIVED",
  favorite: false,
  previewable: false,
  archivedAt: "2026-11-15T10:00:00Z",
  originDate: null,
  purgeAfter: null,
  tags: [{ id: TAG, name: "housing", color: "#4F46E5" }],
};

function renderList() {
  return render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <FileList />
    </QueryClientProvider>,
  );
}

describe("FileList", () => {
  beforeEach(() => {
    // Tag and collection menus load their own lists.
    vi.spyOn(client, "apiFetch").mockImplementation((path: string) =>
      Promise.resolve(
        path.startsWith("/api/v1/tags")
          ? [{ id: TAG, name: "housing", color: null, fileCount: 1 }]
          : [],
      ),
    );
  });
  afterEach(() => {
    vi.restoreAllMocks();
    push.mockReset();
    search = "";
  });

  it("shows an honest empty state when nothing is archived", async () => {
    vi.spyOn(filesApi, "list").mockResolvedValue(page([]));

    renderList();

    expect(await screen.findByText("Your archive is empty")).toBeInTheDocument();
  });

  it("lists files with links to their detail pages", async () => {
    vi.spyOn(filesApi, "list").mockResolvedValue(page([summary]));

    renderList();

    const link = await screen.findByRole("link", { name: /Lease 2021\.pdf/ });
    expect(link).toHaveAttribute("href", "/files/f1");
  });

  it("passes URL filters to the API and defaults to best match when searching", async () => {
    search = `q=lease&tag=${TAG}&type=DOCUMENT&page=1`;
    const list = vi.spyOn(filesApi, "list").mockResolvedValue(page([summary], 2));

    renderList();
    await screen.findByText(/1 result for “lease”/);

    expect(list).toHaveBeenCalledWith(
      { page: 1, size: 20, sort: "relevance", q: "lease", typeGroup: "DOCUMENT", tags: [TAG] },
      expect.anything(),
    );
    expect(await screen.findByRole("list", { name: "Active filters" })).toBeInTheDocument();
  });

  it("searches on Enter and offers to clear filters when nothing matches", async () => {
    search = "q=zzz";
    vi.spyOn(filesApi, "list").mockResolvedValue(page([]));

    renderList();
    expect(await screen.findByText("No files match")).toBeInTheDocument();

    const box = screen.getByRole("searchbox", { name: "Search your archive" });
    await userEvent.clear(box);
    await userEvent.type(box, "lease{Enter}");
    expect(push).toHaveBeenLastCalledWith("/archive?q=lease", { scroll: false });

    await userEvent.click(screen.getByRole("button", { name: "Clear filters" }));
    expect(push).toHaveBeenLastCalledWith("/archive", { scroll: false });
  });
});
