"use client";

import { useCallback, useEffect, useState } from "react";
import { getBackendStatus, type BackendStatus } from "../api/get-health";

export type BackendStatusState = { kind: "loading" } | BackendStatus;

/** Probes the backend once on mount; `refresh` re-probes on demand. */
export function useBackendStatus() {
  const [state, setState] = useState<BackendStatusState>({ kind: "loading" });

  useEffect(() => {
    const controller = new AbortController();
    void getBackendStatus(controller.signal).then((result) => {
      if (!controller.signal.aborted) setState(result);
    });
    return () => controller.abort();
  }, []);

  const refresh = useCallback(async () => {
    setState({ kind: "loading" });
    setState(await getBackendStatus());
  }, []);

  return { state, refresh };
}
