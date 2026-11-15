import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { Heading, Text } from "@/components/ui/typography";
import { cn } from "@/lib/utils";

export type EmptyStateProps = {
  icon: LucideIcon;
  title: string;
  description: ReactNode;
  /** Optional call to action. Only pass actions that actually work. */
  action?: ReactNode;
  /** `compact` fits inside cards and panels; `default` fills a page region. */
  size?: "default" | "compact";
  className?: string;
};

/** Explains why a region is empty and what will appear there. */
export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  size = "default",
  className,
}: EmptyStateProps) {
  const compact = size === "compact";
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center text-center",
        compact ? "gap-3 px-4 py-8" : "gap-4 px-6 py-16",
        className,
      )}
    >
      <div className="relative" aria-hidden>
        {/* Soft halo keeps the icon from floating in empty space. */}
        <div className="bg-primary/10 absolute inset-0 scale-150 rounded-full blur-xl" />
        <div
          className={cn(
            "bg-card text-primary ring-border relative flex items-center justify-center rounded-2xl shadow-sm ring-1",
            compact ? "size-10" : "size-14",
          )}
        >
          <Icon className={compact ? "size-5" : "size-6"} strokeWidth={1.75} />
        </div>
      </div>
      <div className={cn("flex max-w-sm flex-col", compact ? "gap-1" : "gap-1.5")}>
        <Heading level={3} variant={compact ? "subheading" : "heading"}>
          {title}
        </Heading>
        <Text tone="muted" className="text-pretty">
          {description}
        </Text>
      </div>
      {action ? <div className="mt-1 flex flex-wrap justify-center gap-2">{action}</div> : null}
    </div>
  );
}
