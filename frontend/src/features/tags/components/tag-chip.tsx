import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/** Preset colors offered when creating or editing a tag (hex, as stored by the backend). */
export const TAG_COLORS = [
  "#4F46E5",
  "#2563EB",
  "#0891B2",
  "#059669",
  "#65A30D",
  "#D97706",
  "#DC2626",
  "#DB2777",
  "#7C3AED",
  "#475569",
] as const;

/** A tag label with its color dot. */
export function TagChip({
  name,
  color,
  className,
  children,
}: {
  name: string;
  color: string | null;
  className?: string;
  /** Optional trailing control, e.g. a remove button. */
  children?: ReactNode;
}) {
  return (
    <span
      className={cn(
        "bg-secondary text-secondary-foreground inline-flex max-w-full items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium",
        className,
      )}
    >
      <span
        aria-hidden
        className={cn("size-2 shrink-0 rounded-full", color ? "" : "bg-subtle-foreground")}
        style={color ? { backgroundColor: color } : undefined}
      />
      <span className="truncate">{name}</span>
      {children}
    </span>
  );
}

/** Accessible color picker over the preset palette, plus "no color". */
export function ColorPicker({
  value,
  onChange,
  label = "Color",
}: {
  value: string | null;
  onChange: (color: string | null) => void;
  label?: string;
}) {
  return (
    <div role="radiogroup" aria-label={label} className="flex flex-wrap gap-2">
      <button
        type="button"
        role="radio"
        aria-checked={value === null}
        aria-label="No color"
        onClick={() => onChange(null)}
        className={cn(
          "border-input size-6 rounded-full border-2 border-dashed transition-shadow",
          value === null && "ring-ring ring-2 ring-offset-2",
        )}
      />
      {TAG_COLORS.map((color) => (
        <button
          key={color}
          type="button"
          role="radio"
          aria-checked={value === color}
          aria-label={color}
          onClick={() => onChange(color)}
          className={cn(
            "size-6 rounded-full transition-shadow",
            value === color && "ring-ring ring-2 ring-offset-2",
          )}
          style={{ backgroundColor: color }}
        />
      ))}
    </div>
  );
}
