"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Library, MoreHorizontal } from "lucide-react";
import Link from "next/link";
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
import { Textarea } from "@/components/ui/textarea";
import { Text } from "@/components/ui/typography";
import { errorMessage } from "@/lib/api/errors";
import type { CollectionSummary } from "@/lib/api/types";
import { collectionKeys, collectionsApi, useCollections } from "../api/collections-api";

type DialogState =
  | { kind: "none" }
  | { kind: "edit"; collection: CollectionSummary }
  | { kind: "delete"; collection: CollectionSummary };

function useInvalidate() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: collectionKeys.all }),
      queryClient.invalidateQueries({ queryKey: ["files"] }),
    ]);
}

export const collectionHref = (id: string) => `/archive?collection=${encodeURIComponent(id)}`;

/** Collection administration. Membership is managed from each file's page. */
export function CollectionsManager() {
  const collections = useCollections();
  const [dialog, setDialog] = useState<DialogState>({ kind: "none" });
  const close = () => setDialog({ kind: "none" });

  return (
    <div className="flex flex-col gap-6">
      <CreateCollectionForm />
      {collections.isPending ? (
        <ListSkeleton rows={3} />
      ) : collections.isError ? (
        <div role="alert" className="text-destructive text-sm">
          {errorMessage(collections.error)}
        </div>
      ) : collections.data.length === 0 ? (
        <EmptyState
          icon={Library}
          title="No collections yet"
          description="Group related files, like everything about a house purchase. Add files to a collection from each file's page."
        />
      ) : (
        <ul className="divide-border flex flex-col divide-y">
          {collections.data.map((collection) => (
            <li key={collection.id} className="flex items-start gap-3 py-3">
              <span className="bg-accent text-accent-foreground flex size-9 shrink-0 items-center justify-center rounded-lg">
                <Library className="size-4" aria-hidden />
              </span>
              <div className="flex min-w-0 flex-1 flex-col">
                <Link
                  href={collectionHref(collection.id)}
                  className="truncate text-sm font-medium underline-offset-4 hover:underline"
                >
                  {collection.name}
                </Link>
                <Text as="span" variant="caption" tone="muted" className="font-normal">
                  {collection.fileCount} {collection.fileCount === 1 ? "file" : "files"}
                  {collection.description ? ` · ${collection.description}` : ""}
                </Text>
              </div>
              <DropdownMenu>
                <DropdownMenuTrigger
                  render={
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label={`Actions for ${collection.name}`}
                    />
                  }
                >
                  <MoreHorizontal />
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-40">
                  <DropdownMenuItem render={<Link href={collectionHref(collection.id)} />}>
                    View files
                  </DropdownMenuItem>
                  <DropdownMenuItem onClick={() => setDialog({ kind: "edit", collection })}>
                    Edit
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem
                    variant="destructive"
                    onClick={() => setDialog({ kind: "delete", collection })}
                  >
                    Delete
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </li>
          ))}
        </ul>
      )}

      {dialog.kind === "edit" ? (
        <EditCollectionDialog collection={dialog.collection} onClose={close} />
      ) : null}
      {dialog.kind === "delete" ? (
        <DeleteCollectionDialog collection={dialog.collection} onClose={close} />
      ) : null}
    </div>
  );
}

function CreateCollectionForm() {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const invalidate = useInvalidate();
  const create = useMutation({
    mutationFn: () =>
      collectionsApi.create({ name, ...(description.trim() ? { description } : {}) }),
    onSuccess: async (collection) => {
      toast.success(`Collection “${collection.name}” created`);
      setName("");
      setDescription("");
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
        <FieldLabel htmlFor="new-collection">New collection</FieldLabel>
        <div className="flex gap-2">
          <Input
            id="new-collection"
            value={name}
            maxLength={100}
            placeholder="e.g. House purchase"
            aria-invalid={create.isError ? true : undefined}
            onChange={(event) => setName(event.target.value)}
          />
          <Button type="submit" disabled={!name.trim() || create.isPending}>
            Create
          </Button>
        </div>
        {create.isError ? <FieldError>{errorMessage(create.error)}</FieldError> : null}
      </Field>
      <Textarea
        aria-label="Description (optional)"
        placeholder="Description (optional)"
        rows={2}
        maxLength={2000}
        value={description}
        onChange={(event) => setDescription(event.target.value)}
      />
    </form>
  );
}

function EditCollectionDialog({
  collection,
  onClose,
}: {
  collection: CollectionSummary;
  onClose: () => void;
}) {
  const [name, setName] = useState(collection.name);
  const [description, setDescription] = useState(collection.description ?? "");
  const invalidate = useInvalidate();
  const save = useMutation({
    mutationFn: () => collectionsApi.update(collection.id, { name, description }),
    onSuccess: async () => {
      toast.success("Collection updated");
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
            <DialogTitle>Edit collection</DialogTitle>
          </DialogHeader>
          <Field data-invalid={save.isError ? true : undefined}>
            <FieldLabel htmlFor="edit-collection-name">Name</FieldLabel>
            <Input
              id="edit-collection-name"
              value={name}
              maxLength={100}
              onChange={(event) => setName(event.target.value)}
            />
            {save.isError ? <FieldError>{errorMessage(save.error)}</FieldError> : null}
          </Field>
          <Field>
            <FieldLabel htmlFor="edit-collection-description">Description</FieldLabel>
            <Textarea
              id="edit-collection-description"
              rows={3}
              maxLength={2000}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </Field>
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

function DeleteCollectionDialog({
  collection,
  onClose,
}: {
  collection: CollectionSummary;
  onClose: () => void;
}) {
  const invalidate = useInvalidate();
  const remove = useMutation({
    mutationFn: () => collectionsApi.remove(collection.id),
    onSuccess: async () => {
      toast.success(`Collection “${collection.name}” deleted`);
      await invalidate();
      onClose();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Delete “{collection.name}”?</DialogTitle>
          <DialogDescription>
            Only the collection is deleted. Its {collection.fileCount}{" "}
            {collection.fileCount === 1 ? "file stays" : "files stay"} in your archive.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Keep it
          </Button>
          <Button variant="destructive" disabled={remove.isPending} onClick={() => remove.mutate()}>
            Delete collection
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
