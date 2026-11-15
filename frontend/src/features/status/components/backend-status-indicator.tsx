"use client";

import Link from "next/link";
import { cn } from "@/lib/utils";
import { useBackendStatus } from "../hooks/use-backend-status";

const VIEW = {
  loading: { label: "Checking", dot: "bg-subtle-foreground animate-pulse" },
  up: { label: "All systems normal", dot: "bg-success" },
  degraded: { label: "Degraded", dot: "bg-warning" },
  unreachable: { label: "Backend offline", dot: "bg-destructive" },
} as const;

/** Compact live health pill for the top navigation. Links to the full status page. */
export function BackendStatusIndicator({ className }: { className?: string }) {
  const { state } = useBackendStatus();
  const { label, dot } = VIEW[state.kind];

  return (
    <Link
      href="/status"
      className={cn(
        "text-muted-foreground hover:text-foreground hover:bg-secondary type-caption inline-flex items-center gap-2 rounded-full px-2.5 py-1 transition-colors",
        className,
      )}
    >
      <span className="relative flex size-2" aria-hidden>
        {state.kind === "up" ? (
          <span className="bg-success/40 absolute inline-flex size-full animate-ping rounded-full [animation-duration:2.5s]" />
        ) : null}
        <span className={cn("relative inline-flex size-2 rounded-full", dot)} />
      </span>
      <span aria-live="polite">{label}</span>
    </Link>
  );
}
