"use client";

import { CircleAlert, CircleCheck, CircleX, Loader2, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { getBackendStatus, type BackendStatus } from "../api/get-health";

type ViewState = { kind: "loading" } | BackendStatus;

const LABELS = {
  loading: { text: "Checking…", Icon: Loader2 },
  up: { text: "Operational", Icon: CircleCheck },
  degraded: { text: "Degraded", Icon: CircleAlert },
  unreachable: { text: "Unreachable", Icon: CircleX },
} as const;

const DESCRIPTIONS = {
  loading: "Contacting the backend through the application proxy.",
  up: "The backend is running and its database is reachable.",
  degraded: "The backend is running but its database is not responding.",
  unreachable: "The backend could not be reached. Is it running on the configured BACKEND_URL?",
} as const;

export function BackendStatusCard() {
  const [state, setState] = useState<ViewState>({ kind: "loading" });

  const refresh = useCallback(async (signal?: AbortSignal) => {
    setState({ kind: "loading" });
    const result = await getBackendStatus(signal);
    if (!signal?.aborted) setState(result);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    // Initial probe; later probes come from the Refresh button.
    void getBackendStatus(controller.signal).then((result) => {
      if (!controller.signal.aborted) setState(result);
    });
    return () => controller.abort();
  }, []);

  const { text, Icon } = LABELS[state.kind];
  const variant =
    state.kind === "up" ? "default" : state.kind === "loading" ? "secondary" : "destructive";

  return (
    <Card className="w-full max-w-md">
      <CardHeader>
        <CardTitle className="flex items-center justify-between gap-4">
          Backend status
          <Badge variant={variant} aria-live="polite">
            <Icon className={state.kind === "loading" ? "animate-spin" : undefined} aria-hidden />
            {text}
          </Badge>
        </CardTitle>
        <CardDescription>{DESCRIPTIONS[state.kind]}</CardDescription>
      </CardHeader>
      <CardContent className="flex items-center justify-between gap-4">
        <span className="text-muted-foreground text-sm">
          {state.kind === "up" || state.kind === "degraded"
            ? `Database: ${state.health.database}`
            : " "}
        </span>
        <Button
          variant="outline"
          size="sm"
          onClick={() => void refresh()}
          disabled={state.kind === "loading"}
        >
          <RefreshCw aria-hidden />
          Refresh
        </Button>
      </CardContent>
    </Card>
  );
}
