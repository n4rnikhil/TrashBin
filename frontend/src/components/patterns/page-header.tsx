import type { ReactNode } from "react";
import { Heading, Text } from "@/components/ui/typography";
import { cn } from "@/lib/utils";

export type PageHeaderProps = {
  title: string;
  description?: ReactNode;
  /** Right-aligned actions; wraps below the title on small screens. */
  actions?: ReactNode;
  className?: string;
};

export function PageHeader({ title, description, actions, className }: PageHeaderProps) {
  return (
    <header
      className={cn(
        "flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between sm:gap-6",
        className,
      )}
    >
      <div className="flex min-w-0 flex-col gap-1">
        <Heading level={1} variant="title">
          {title}
        </Heading>
        {description ? <Text tone="muted">{description}</Text> : null}
      </div>
      {actions ? <div className="flex shrink-0 flex-wrap gap-2">{actions}</div> : null}
    </header>
  );
}
