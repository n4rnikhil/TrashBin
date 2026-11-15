"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { collectionKeys } from "@/features/collections";
import { tagKeys } from "@/features/tags";
import { fileKeys, filesApi } from "../api/files-api";

/** Discard, restore and purge. Every change refreshes lists, counts and the file itself. */
export function useLifecycle(fileId: string) {
  const queryClient = useQueryClient();
  const refreshEverything = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: fileKeys.all }),
      queryClient.invalidateQueries({ queryKey: tagKeys.all }),
      queryClient.invalidateQueries({ queryKey: collectionKeys.all }),
    ]);

  return {
    discard: useMutation({
      mutationFn: () => filesApi.discard(fileId),
      onSuccess: async (file) => {
        queryClient.setQueryData(fileKeys.detail(fileId), file);
        await refreshEverything();
      },
    }),
    restore: useMutation({
      mutationFn: () => filesApi.restore(fileId),
      onSuccess: async (file) => {
        queryClient.setQueryData(fileKeys.detail(fileId), file);
        await refreshEverything();
      },
    }),
    purge: useMutation({
      mutationFn: () => filesApi.purge(fileId),
      onSuccess: async () => {
        queryClient.removeQueries({ queryKey: fileKeys.detail(fileId) });
        await refreshEverything();
      },
    }),
  };
}
