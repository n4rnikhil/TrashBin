"use client";

import { Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import type { ReactNode } from "react";
import { useAuth } from "./auth-provider";

function FullPageSpinner({ label }: { label: string }) {
  return (
    <div role="status" className="flex flex-1 items-center justify-center p-8">
      <Loader2 className="text-muted-foreground size-5 animate-spin" aria-hidden />
      <span className="sr-only">{label}</span>
    </div>
  );
}

/** Renders children only for signed-in users; everyone else is sent to the sign-in page. */
export function AuthGate({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (state.status === "anonymous") router.replace("/login");
  }, [state.status, router]);

  if (state.status !== "authenticated") return <FullPageSpinner label="Checking your session" />;
  return <>{children}</>;
}

/** For sign-in/sign-up pages: signed-in users go straight to the app. */
export function AnonymousOnly({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (state.status === "authenticated") router.replace("/dashboard");
  }, [state.status, router]);

  if (state.status !== "anonymous") return <FullPageSpinner label="Loading" />;
  return <>{children}</>;
}
