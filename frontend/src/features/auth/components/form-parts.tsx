import { CircleAlert, Loader2 } from "lucide-react";
import type { ComponentProps, ReactNode } from "react";
import type { FieldValues, Path, UseFormSetError } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { ApiError, errorMessage } from "@/lib/api/errors";

/** Labeled input with help text and an inline error, wired for assistive technology. */
export function TextField({
  id,
  label,
  description,
  error,
  ...input
}: {
  id: string;
  label: string;
  description?: string;
  error?: string | undefined;
} & ComponentProps<typeof Input>) {
  const describedBy = error ? `${id}-error` : description ? `${id}-help` : undefined;
  return (
    <Field data-invalid={error ? true : undefined}>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <Input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        {...input}
      />
      {error ? (
        <FieldError id={`${id}-error`}>{error}</FieldError>
      ) : description ? (
        <FieldDescription id={`${id}-help`}>{description}</FieldDescription>
      ) : null}
    </Field>
  );
}

/** Form-level error banner. */
export function FormAlert({ message }: { message: string | undefined }) {
  if (!message) return null;
  return (
    <div
      role="alert"
      className="bg-destructive/10 text-destructive flex items-start gap-2 rounded-lg px-3 py-2.5 text-sm"
    >
      <CircleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
      <span>{message}</span>
    </div>
  );
}

export function SubmitButton({
  pending,
  children,
  ...props
}: { pending: boolean; children: ReactNode } & ComponentProps<typeof Button>) {
  return (
    <Button type="submit" disabled={pending} {...props}>
      {pending ? <Loader2 className="animate-spin" aria-hidden /> : null}
      {children}
    </Button>
  );
}

/**
 * Maps a failed API call onto a form: per-field messages where the server named a field the form
 * has, otherwise a single form-level message.
 */
export function applyApiError<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
): void {
  if (error instanceof ApiError) {
    const known = error.fieldErrors.filter((e) => (fields as readonly string[]).includes(e.field));
    if (known.length > 0) {
      known.forEach((e) => setError(e.field as Path<T>, { message: e.message }));
      return;
    }
  }
  setError("root", { message: errorMessage(error) });
}
