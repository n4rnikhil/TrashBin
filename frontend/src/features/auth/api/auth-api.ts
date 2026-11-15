import { apiFetch } from "@/lib/api/client";
import type {
  AuthResponse,
  ChangePasswordRequest,
  LoginRequest,
  RegisterRequest,
  SessionInfo,
  UserProfile,
} from "@/lib/api/types";

export const authApi = {
  register: (body: RegisterRequest) =>
    apiFetch<UserProfile>("/api/v1/auth/register", { method: "POST", body }),

  login: (body: LoginRequest) =>
    apiFetch<AuthResponse>("/api/v1/auth/login", { method: "POST", body }),

  logout: () => apiFetch<void>("/api/v1/auth/logout", { method: "POST", cookieGuard: true }),

  logoutEverywhere: () => apiFetch<void>("/api/v1/auth/logout-all", { method: "POST" }),

  updateProfile: (displayName: string) =>
    apiFetch<UserProfile>("/api/v1/users/me", { method: "PATCH", body: { displayName } }),

  changePassword: (body: ChangePasswordRequest) =>
    apiFetch<void>("/api/v1/users/me/password", { method: "POST", body }),

  listSessions: (signal?: AbortSignal) =>
    apiFetch<SessionInfo[]>("/api/v1/users/me/sessions", signal ? { signal } : {}),

  revokeSession: (sessionId: string) =>
    apiFetch<void>(`/api/v1/users/me/sessions/${encodeURIComponent(sessionId)}`, {
      method: "DELETE",
    }),
};
