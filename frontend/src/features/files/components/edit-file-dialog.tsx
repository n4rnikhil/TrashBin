"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { ApiError, errorMessage } from "@/lib/api/errors";
import type { FileDetail } from "@/lib/api/types";
import { useFileMutations } from "../hooks/use-file-mutations";

const schema = z.object({
  displayName: z.string().trim().min(1, "Enter a name.").max(255, "Name is too long."),
  contextNote: z.string().max(2000, "Keep it under 2,000 characters."),
  notes: z.string().max(10000, "Keep it under 10,000 characters."),
  originDate: z.string().regex(/^$|^\d{4}-\d{2}-\d{2}$/, "Enter a valid date."),
});
type Values = z.infer<typeof schema>;

/**
 * Edits a file's descriptive metadata. Sends the version the user is looking at; if someone else
 * changed the file meanwhile the server refuses (412) and we offer to reload instead of overwriting.
 */
export function EditFileDialog({
  file,
  open,
  onOpenChange,
}: {
  file: FileDetail;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { update, reload } = useFileMutations(file.id);
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    values: {
      displayName: file.displayName,
      contextNote: file.contextNote ?? "",
      notes: file.notes ?? "",
      originDate: file.originDate ?? "",
    },
  });

  const conflict = errors.root?.type === "conflict";

  const onSubmit = handleSubmit(async (values) => {
    try {
      await update.mutateAsync({ version: file.version, body: values });
      toast.success("Details saved");
      onOpenChange(false);
    } catch (error) {
      if (error instanceof ApiError && error.code === "VERSION_CONFLICT") {
        setError("root", {
          type: "conflict",
          message: "This file was changed somewhere else. Reload to see the latest version.",
        });
      } else {
        setError("root", { message: errorMessage(error) });
      }
    }
  });

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) reset();
        onOpenChange(next);
      }}
    >
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>Edit details</DialogTitle>
            <DialogDescription>The original file is never changed.</DialogDescription>
          </DialogHeader>

          {errors.root ? (
            <div
              role="alert"
              className="bg-destructive/10 text-destructive flex flex-col gap-2 rounded-lg px-3 py-2.5 text-sm"
            >
              {errors.root.message}
              {conflict ? (
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  className="self-start"
                  onClick={async () => {
                    await reload();
                    reset();
                  }}
                >
                  Reload latest version
                </Button>
              ) : null}
            </div>
          ) : null}

          <Field data-invalid={errors.displayName ? true : undefined}>
            <FieldLabel htmlFor="edit-name">Name</FieldLabel>
            <Input id="edit-name" {...register("displayName")} />
            {errors.displayName ? <FieldError>{errors.displayName.message}</FieldError> : null}
          </Field>
          <Field data-invalid={errors.contextNote ? true : undefined}>
            <FieldLabel htmlFor="edit-context">Why are you keeping this?</FieldLabel>
            <Textarea id="edit-context" rows={2} {...register("contextNote")} />
            {errors.contextNote ? <FieldError>{errors.contextNote.message}</FieldError> : null}
          </Field>
          <Field data-invalid={errors.notes ? true : undefined}>
            <FieldLabel htmlFor="edit-notes">Notes</FieldLabel>
            <Textarea id="edit-notes" rows={4} {...register("notes")} />
            {errors.notes ? <FieldError>{errors.notes.message}</FieldError> : null}
          </Field>
          <Field data-invalid={errors.originDate ? true : undefined}>
            <FieldLabel htmlFor="edit-origin">Origin date</FieldLabel>
            <Input id="edit-origin" type="date" {...register("originDate")} />
            <FieldDescription>When the file is from, if that differs from today.</FieldDescription>
            {errors.originDate ? <FieldError>{errors.originDate.message}</FieldError> : null}
          </Field>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              Save
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
