"use client";

import { Pencil } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Text } from "@/components/ui/typography";
import { TagChip, useTags } from "@/features/tags";
import { errorMessage } from "@/lib/api/errors";
import type { FileDetail } from "@/lib/api/types";
import { useFileMutations } from "../hooks/use-file-mutations";
import { TagEditor } from "./tag-editor";

/** Shows a file's tags and switches to an editor in place. */
export function FileTagsCard({ file }: { file: FileDetail }) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<string[]>([]);
  const { setTags } = useFileMutations(file.id);
  const tags = useTags();
  const colors = Object.fromEntries(
    [...(tags.data ?? []), ...file.tags].map((t) => [t.name.toLowerCase(), t.color]),
  );

  const startEditing = () => {
    setDraft(file.tags.map((t) => t.name));
    setTags.reset();
    setEditing(true);
  };

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>Tags</CardTitle>
        {!editing ? (
          <Button variant="ghost" size="sm" onClick={startEditing}>
            <Pencil aria-hidden /> Edit tags
          </Button>
        ) : null}
      </CardHeader>
      <CardContent>
        {editing ? (
          <div className="flex flex-col gap-3">
            <TagEditor
              value={draft}
              onChange={setDraft}
              suggestions={(tags.data ?? []).map((t) => t.name)}
              colors={colors}
            />
            {setTags.isError ? (
              <p role="alert" className="text-destructive text-sm">
                {errorMessage(setTags.error)}
              </p>
            ) : null}
            <div className="flex gap-2">
              <Button
                disabled={setTags.isPending}
                onClick={() =>
                  setTags.mutate(draft, {
                    onSuccess: () => setEditing(false),
                  })
                }
              >
                Save tags
              </Button>
              <Button variant="outline" onClick={() => setEditing(false)}>
                Cancel
              </Button>
            </div>
          </div>
        ) : file.tags.length > 0 ? (
          <ul className="flex flex-wrap gap-1.5" aria-label="Tags">
            {file.tags.map((tag) => (
              <li key={tag.id}>
                <TagChip name={tag.name} color={tag.color} />
              </li>
            ))}
          </ul>
        ) : (
          <Text tone="muted">No tags yet. Tags make files easier to find later.</Text>
        )}
      </CardContent>
    </Card>
  );
}
