"use client";

import { useQueryClient } from "@tanstack/react-query";
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { onSessionExpired, refreshSession, tokenStore } from "@/lib/api/client";
import type { UserProfile } from "@/lib/api/types";
import { authApi } from "../api/auth-api";

type AuthState =
  { status: "loading" } | { status: "authenticated"; user: UserProfile } | { status: "anonymous" };

type AuthContextValue = {
  state: AuthState;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  /** Forget the session locally (after the server already ended it, e.g. password change). */
  clearSession: () => void;
  setUser: (user: UserProfile) => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: "loading" });
  const queryClient = useQueryClient();

  const clearSession = useCallback(() => {
    tokenStore.set(null);
    queryClient.clear(); // never show one account's cached data to the next
    setState({ status: "anonymous" });
  }, [queryClient]);

  // On load the access token is gone (memory only); the refresh cookie restores the session.
  useEffect(() => {
    let cancelled = false;
    void refreshSession().then((result) => {
      if (cancelled) return;
      setState(result ? { status: "authenticated", user: result.user } : { status: "anonymous" });
    });
    const unsubscribe = onSessionExpired(clearSession);
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [clearSession]);

  const login = useCallback(async (email: string, password: string) => {
    const result = await authApi.login({ email, password });
    tokenStore.set(result.accessToken);
    setState({ status: "authenticated", user: result.user });
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      // Even if the request failed, drop local credentials: the user asked to leave.
      clearSession();
    }
  }, [clearSession]);

  const setUser = useCallback(
    (user: UserProfile) => setState({ status: "authenticated", user }),
    [],
  );

  const value = useMemo(
    () => ({ state, login, logout, clearSession, setUser }),
    [state, login, logout, clearSession, setUser],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside <AuthProvider>");
  return context;
}

/** The signed-in user. Only call beneath <AuthGate>, which guarantees one exists. */
export function useCurrentUser(): UserProfile {
  const { state } = useAuth();
  if (state.status !== "authenticated") throw new Error("useCurrentUser requires <AuthGate>");
  return state.user;
}
