import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
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
  archivedAt: "2026-11-15T10:00:00Z",
  originDate: null,
  tags: [{ id: "t1", name: "housing", color: "#4F46E5" }],
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
  afterEach(() => {
    vi.restoreAllMocks();
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
    expect(screen.getByText(/1\.5 KB/)).toBeInTheDocument();
  });

  it("reads page and sort from the URL and ignores unknown sorts", async () => {
    search = "page=2&sort=sha256,asc";
    const list = vi.spyOn(filesApi, "list").mockResolvedValue(page([summary], 3));

    renderList();
    await screen.findByText(/Page 3 of 3/);

    expect(list).toHaveBeenCalledWith(
      { page: 2, size: 20, sort: "archivedAt,desc" },
      expect.anything(),
    );
  });
});
