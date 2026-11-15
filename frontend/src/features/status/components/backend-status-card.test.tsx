import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as api from "../api/get-health";
import { BackendStatusCard } from "./backend-status-card";

const health = { status: "UP", database: "UP", timestamp: "2026-11-15T10:15:30Z" };

describe("BackendStatusCard", () => {
  afterEach(() => vi.restoreAllMocks());

  it("shows an operational state when the backend is up", async () => {
    vi.spyOn(api, "getBackendStatus").mockResolvedValue({ kind: "up", health });

    render(<BackendStatusCard />);

    expect(await screen.findByText("Operational")).toBeInTheDocument();
    expect(screen.getByText("Database: UP")).toBeInTheDocument();
  });

  it("shows unreachable when the backend cannot be contacted", async () => {
    vi.spyOn(api, "getBackendStatus").mockResolvedValue({ kind: "unreachable" });

    render(<BackendStatusCard />);

    expect(await screen.findByText("Unreachable")).toBeInTheDocument();
  });

  it("re-checks when Refresh is clicked", async () => {
    const spy = vi
      .spyOn(api, "getBackendStatus")
      .mockResolvedValueOnce({ kind: "unreachable" })
      .mockResolvedValueOnce({ kind: "up", health });

    render(<BackendStatusCard />);
    await screen.findByText("Unreachable");
    await userEvent.click(screen.getByRole("button", { name: /refresh/i }));

    expect(await screen.findByText("Operational")).toBeInTheDocument();
    expect(spy).toHaveBeenCalledTimes(2);
  });
});
