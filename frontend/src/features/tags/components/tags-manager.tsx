"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { MoreHorizontal, Tags } from "lucide-react";
import { useState } from "react";
import type { FormEvent } from "react";
import { toast } from "sonner";
import { EmptyState } from "@/components/patterns/empty-state";
import { ListSkeleton } from "@/components/patterns/skeletons";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Text } from "@/components/ui/typography";
import { errorMessage } from "@/lib/api/errors";
import type { TagSummary } from "@/lib/api/types";
import { tagKeys, tagsApi, useTags } from "../api/tags-api";
import { ColorPicker, TagChip } from "./tag-chip";

type DialogState =
  | { kind: "none" }
  | { kind: "edit"; tag: TagSummary }
  | { kind: "merge"; tag: TagSummary }
  | { kind: "delete"; tag: TagSummary };

function useInvalidateTagsAndFiles() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: tagKeys.all }),
      queryClient.invalidateQueries({ queryKey: ["files"] }),
    ]);
}

/** Tag administration: create, rename/recolor, merge and delete. */
export function TagsManager() {
  const tags = useTags();
  const [dialog, setDialog] = useState<DialogState>({ kind: "none" });
  const close = () => setDialog({ kind: "none" });

  return (
    <div className="flex flex-col gap-6">
      <CreateTagForm />
      {tags.isPending ? (
        <ListSkeleton rows={4} />
      ) : tags.isError ? (
        <div role="alert" className="text-destructive text-sm">
          {errorMessage(tags.error)}
        </div>
      ) : tags.data.length === 0 ? (
        <EmptyState
          icon={Tags}
          title="No tags yet"
          description="Create one above, or add tags to a file from its detail page."
        />
      ) : (
        <ul className="divide-border flex flex-col divide-y">
          {tags.data.map((tag) => (
            <li key={tag.id} className="flex items-center gap-3 py-3">
              <TagChip name={tag.name} color={tag.color} className="text-sm" />
              <Text as="span" variant="caption" tone="muted" className="flex-1 font-normal">
                {tag.fileCount} {tag.fileCount === 1 ? "file" : "files"}
              </Text>
              <DropdownMenu>
                <DropdownMenuTrigger
                  render={
                    <Button variant="ghost" size="icon-sm" aria-label={`Actions for ${tag.name}`} />
                  }
                >
                  <MoreHorizontal />
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-44">
                  <DropdownMenuItem onClick={() => setDialog({ kind: "edit", tag })}>
                    Rename or recolor
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    disabled={tags.data.length < 2}
                    onClick={() => setDialog({ kind: "merge", tag })}
                  >
                    Merge into…
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem
                    variant="destructive"
                    onClick={() => setDialog({ kind: "delete", tag })}
                  >
                    Delete
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </li>
          ))}
        </ul>
      )}

      {dialog.kind === "edit" ? <EditTagDialog tag={dialog.tag} onClose={close} /> : null}
      {dialog.kind === "merge" && tags.data ? (
        <MergeTagDialog tag={dialog.tag} all={tags.data} onClose={close} />
      ) : null}
      {dialog.kind === "delete" ? <DeleteTagDialog tag={dialog.tag} onClose={close} /> : null}
    </div>
  );
}

function CreateTagForm() {
  const [name, setName] = useState("");
  const [color, setColor] = useState<string | null>(null);
  const invalidate = useInvalidateTagsAndFiles();
  const create = useMutation({
    mutationFn: () => tagsApi.create({ name, ...(color ? { color } : {}) }),
    onSuccess: async (tag) => {
      toast.success(`Tag “${tag.name}” created`);
      setName("");
      setColor(null);
      await invalidate();
    },
  });

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (name.trim()) create.mutate();
  };

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-3" noValidate>
      <Field data-invalid={create.isError ? true : undefined}>
        <FieldLabel htmlFor="new-tag">New tag</FieldLabel>
        <div className="flex gap-2">
          <Input
            id="new-tag"
            value={name}
            maxLength={50}
            placeholder="e.g. Taxes"
            aria-invalid={create.isError ? true : undefined}
            onChange={(event) => setName(event.target.value)}
          />
          <Button type="submit" disabled={!name.trim() || create.isPending}>
            Add tag
          </Button>
        </div>
        {create.isError ? <FieldError>{errorMessage(create.error)}</FieldError> : null}
      </Field>
      <ColorPicker value={color} onChange={setColor} label="Color for the new tag" />
    </form>
  );
}

function EditTagDialog({ tag, onClose }: { tag: TagSummary; onClose: () => void }) {
  const [name, setName] = useState(tag.name);
  const [color, setColor] = useState<string | null>(tag.color);
  const invalidate = useInvalidateTagsAndFiles();
  const save = useMutation({
    mutationFn: () => tagsApi.update(tag.id, { name, color: color ?? "" }),
    onSuccess: async () => {
      toast.success("Tag updated");
      await invalidate();
      onClose();
    },
  });

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            save.mutate();
          }}
          className="flex flex-col gap-4"
        >
          <DialogHeader>
            <DialogTitle>Edit tag</DialogTitle>
            <DialogDescription>Changes apply to every file with this tag.</DialogDescription>
          </DialogHeader>
          <Field data-invalid={save.isError ? true : undefined}>
            <FieldLabel htmlFor="edit-tag-name">Name</FieldLabel>
            <Input
              id="edit-tag-name"
              value={name}
              maxLength={50}
              onChange={(event) => setName(event.target.value)}
            />
            {save.isError ? <FieldError>{errorMessage(save.error)}</FieldError> : null}
          </Field>
          <ColorPicker value={color} onChange={setColor} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={!name.trim() || save.isPending}>
              Save
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function MergeTagDialog({
  tag,
  all,
  onClose,
}: {
  tag: TagSummary;
  all: TagSummary[];
  onClose: () => void;
}) {
  const targets = all.filter((t) => t.id !== tag.id);
  const [targetId, setTargetId] = useState(targets[0]?.id ?? "");
  const invalidate = useInvalidateTagsAndFiles();
  const merge = useMutation({
    mutationFn: () => tagsApi.merge(tag.id, targetId),
    onSuccess: async (target) => {
      toast.success(`Merged into “${target.name}”`);
      await invalidate();
      onClose();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Merge “{tag.name}”</DialogTitle>
          <DialogDescription>
            Its {tag.fileCount} {tag.fileCount === 1 ? "file moves" : "files move"} to the tag you
            choose, then “{tag.name}” is deleted.
          </DialogDescription>
        </DialogHeader>
        <Field>
          <FieldLabel htmlFor="merge-target">Merge into</FieldLabel>
          <select
            id="merge-target"
            value={targetId}
            onChange={(event) => setTargetId(event.target.value)}
            className="border-input bg-background focus-visible:ring-ring/50 h-9 rounded-md border px-3 text-sm outline-none focus-visible:ring-3"
          >
            {targets.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </Field>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button disabled={!targetId || merge.isPending} onClick={() => merge.mutate()}>
            Merge
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function DeleteTagDialog({ tag, onClose }: { tag: TagSummary; onClose: () => void }) {
  const invalidate = useInvalidateTagsAndFiles();
  const remove = useMutation({
    mutationFn: () => tagsApi.remove(tag.id),
    onSuccess: async () => {
      toast.success(`Tag “${tag.name}” deleted`);
      await invalidate();
      onClose();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Delete “{tag.name}”?</DialogTitle>
          <DialogDescription>
            The tag is removed from {tag.fileCount} {tag.fileCount === 1 ? "file" : "files"}. The
            files themselves are not affected.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Keep it
          </Button>
          <Button variant="destructive" disabled={remove.isPending} onClick={() => remove.mutate()}>
            Delete tag
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
