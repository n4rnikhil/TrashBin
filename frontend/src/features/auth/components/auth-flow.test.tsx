import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as client from "@/lib/api/client";
import { ApiError } from "@/lib/api/errors";
import { authApi } from "../api/auth-api";
import { AuthGate } from "./auth-gate";
import { AuthProvider } from "./auth-provider";
import { LoginForm } from "./login-form";

const replace = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));

const user = { id: "u1", email: "sam@example.com", displayName: "Sam", role: "USER" };

function renderWithProviders(ui: ReactNode) {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider>{ui}</AuthProvider>
    </QueryClientProvider>,
  );
}

describe("authentication UI", () => {
  beforeEach(() => {
    replace.mockReset();
    client.tokenStore.set(null);
  });
  afterEach(() => vi.restoreAllMocks());

  it("AuthGate redirects anonymous visitors to the sign-in page and hides content", async () => {
    vi.spyOn(client, "refreshSession").mockResolvedValue(null);

    renderWithProviders(<AuthGate>secret content</AuthGate>);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
    expect(screen.queryByText("secret content")).not.toBeInTheDocument();
  });

  it("AuthGate shows content once the session is restored from the refresh cookie", async () => {
    vi.spyOn(client, "refreshSession").mockResolvedValue({
      accessToken: "t",
      expiresIn: 600,
      user,
    });

    renderWithProviders(<AuthGate>secret content</AuthGate>);

    expect(await screen.findByText("secret content")).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });

  it("LoginForm validates locally before calling the server", async () => {
    vi.spyOn(client, "refreshSession").mockResolvedValue(null);
    const login = vi.spyOn(authApi, "login");
    renderWithProviders(<LoginForm />);

    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByText("Enter a valid email address.")).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it("LoginForm shows the server's message on wrong credentials and keeps the token in memory only", async () => {
    vi.spyOn(client, "refreshSession").mockResolvedValue(null);
    vi.spyOn(authApi, "login").mockRejectedValue(
      new ApiError(401, "INVALID_CREDENTIALS", "The email or password is incorrect."),
    );
    renderWithProviders(<LoginForm />);

    await userEvent.type(screen.getByLabelText("Email"), "sam@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "wrong password!!");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The email or password is incorrect.",
    );
    expect(client.tokenStore.get()).toBeNull();
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });

  it("LoginForm stores the access token in memory after a successful sign-in", async () => {
    vi.spyOn(client, "refreshSession").mockResolvedValue(null);
    vi.spyOn(authApi, "login").mockResolvedValue({ accessToken: "tok", expiresIn: 600, user });
    renderWithProviders(<LoginForm />);

    await userEvent.type(screen.getByLabelText("Email"), "sam@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "correct horse battery");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => expect(client.tokenStore.get()).toBe("tok"));
    expect(window.localStorage.length).toBe(0);
  });
});
