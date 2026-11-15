"use client";

import { CircleAlert, CircleCheck, CircleX, Loader2, RefreshCw } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useBackendStatus } from "../hooks/use-backend-status";

const LABELS = {
  loading: { text: "Checking…", Icon: Loader2, variant: "secondary" },
  up: { text: "Operational", Icon: CircleCheck, variant: "success" },
  degraded: { text: "Degraded", Icon: CircleAlert, variant: "warning" },
  unreachable: { text: "Unreachable", Icon: CircleX, variant: "destructive" },
} as const;

const DESCRIPTIONS = {
  loading: "Contacting the backend through the application proxy.",
  up: "The backend is running and its database is reachable.",
  degraded: "The backend is running but its database is not responding.",
  unreachable: "The backend could not be reached. Is it running on the configured BACKEND_URL?",
} as const;

export function BackendStatusCard({ className }: { className?: string }) {
  const { state, refresh } = useBackendStatus();
  const { text, Icon, variant } = LABELS[state.kind];

  return (
    <Card className={className}>
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
        <span className="text-muted-foreground type-caption">
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
