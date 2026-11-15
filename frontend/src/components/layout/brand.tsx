import { Trash2 } from "lucide-react";
import { cn } from "@/lib/utils";

/** Brand mark: indigo tile with the bin glyph. */
export function BrandMark({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        "from-primary text-primary-foreground flex size-8 shrink-0 items-center justify-center rounded-lg bg-linear-to-br to-indigo-500 shadow-sm ring-1 ring-black/5",
        className,
      )}
      aria-hidden
    >
      <Trash2 className="size-4" strokeWidth={2.25} />
    </span>
  );
}

/** Full lockup: mark, wordmark and tagline. */
export function BrandLockup({ className }: { className?: string }) {
  return (
    <span className={cn("flex min-w-0 items-center gap-2.5", className)}>
      <BrandMark />
      <span className="flex min-w-0 flex-col leading-none">
        <span className="text-foreground truncate text-[0.8125rem] font-semibold tracking-[0.08em]">
          TRASH BIN
        </span>
        <span className="text-muted-foreground mt-1 truncate text-[0.6875rem]">
          Stores Your Trash.
        </span>
      </span>
    </span>
  );
}
