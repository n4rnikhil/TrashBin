"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Text } from "@/components/ui/typography";
import { collectionHref, collectionKeys, useCollections } from "@/features/collections";
import { errorMessage } from "@/lib/api/errors";
import type { FileDetail } from "@/lib/api/types";
import { fileKeys, filesApi } from "../api/files-api";

/** Which collections a file belongs to, editable with checkboxes. */
export function FileCollectionsCard({ file }: { file: FileDetail }) {
  const collections = useCollections();
  const queryClient = useQueryClient();
  const current = file.collections.map((c) => c.id);
  const [selected, setSelected] = useState<string[]>(current);
  const [lastCurrent, setLastCurrent] = useState(current.join());
  if (current.join() !== lastCurrent) {
    // The file changed (saved here or elsewhere): start from its new membership.
    setLastCurrent(current.join());
    setSelected(current);
  }

  const save = useMutation({
    mutationFn: () => filesApi.setCollections(file.id, selected),
    onSuccess: async (updated) => {
      queryClient.setQueryData(fileKeys.detail(file.id), updated);
      toast.success("Collections saved");
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: collectionKeys.all }),
        queryClient.invalidateQueries({ queryKey: fileKeys.lists }),
      ]);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const dirty = [...selected].sort().join() !== [...current].sort().join();

  return (
    <Card>
      <CardHeader>
        <CardTitle>Collections</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {collections.isPending ? (
          <Text tone="muted">Loading collections…</Text>
        ) : !collections.data?.length ? (
          <Text tone="muted">
            No collections yet.{" "}
            <Link href="/collections" className="text-primary underline-offset-4 hover:underline">
              Create one
            </Link>{" "}
            to group related files.
          </Text>
        ) : (
          <>
            <fieldset className="flex flex-col gap-2">
              <legend className="sr-only">Collections for this file</legend>
              {collections.data.map((c) => (
                <label key={c.id} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="accent-primary size-4"
                    checked={selected.includes(c.id)}
                    onChange={(event) =>
                      setSelected((prev) =>
                        event.target.checked ? [...prev, c.id] : prev.filter((id) => id !== c.id),
                      )
                    }
                  />
                  <span className="flex-1">{c.name}</span>
                  <Link
                    href={collectionHref(c.id)}
                    className="text-muted-foreground text-xs underline-offset-4 hover:underline"
                  >
                    View
                  </Link>
                </label>
              ))}
            </fieldset>
            <Button
              className="self-start"
              disabled={!dirty || save.isPending}
              onClick={() => save.mutate()}
            >
              Save collections
            </Button>
          </>
        )}
      </CardContent>
    </Card>
  );
}
