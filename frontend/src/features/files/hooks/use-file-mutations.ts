"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { tagKeys } from "@/features/tags";
import type { FileDetail, UpdateFileRequest } from "@/lib/api/types";
import { fileKeys, filesApi } from "../api/files-api";

/** Mutations on one file. Each keeps the detail cache fresh and refreshes lists. */
export function useFileMutations(id: string) {
  const queryClient = useQueryClient();
  const onSaved = async (file: FileDetail) => {
    queryClient.setQueryData(fileKeys.detail(id), file);
    await queryClient.invalidateQueries({ queryKey: fileKeys.lists });
  };

  return {
    update: useMutation({
      mutationFn: ({ version, body }: { version: number; body: UpdateFileRequest }) =>
        filesApi.update(id, version, body),
      onSuccess: onSaved,
    }),
    setTags: useMutation({
      mutationFn: (tags: string[]) => filesApi.setTags(id, tags),
      onSuccess: async (file) => {
        await onSaved(file);
        await queryClient.invalidateQueries({ queryKey: tagKeys.all }); // counts, new tags
      },
    }),
    setFavorite: useMutation({
      mutationFn: (favorite: boolean) => filesApi.setFavorite(id, favorite),
      onSuccess: onSaved,
    }),
    /** Reload after a conflict so the user sees the latest version. */
    reload: () => queryClient.invalidateQueries({ queryKey: fileKeys.detail(id) }),
  };
}
