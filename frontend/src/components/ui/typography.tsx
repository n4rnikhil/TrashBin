import type { ComponentProps, ElementType } from "react";
import { cn } from "@/lib/utils";

/**
 * Typography system. Styles map 1:1 to the `type-*` utilities in globals.css; prefer these
 * components for semantic headings and body copy, and the utilities for one-off styling.
 */
const HEADING_STYLES = {
  display: "type-display",
  title: "type-title",
  heading: "type-heading",
  subheading: "type-subheading",
} as const;

type HeadingLevel = 1 | 2 | 3 | 4;

export type HeadingProps = ComponentProps<"h2"> & {
  /** Semantic level; independent of the visual variant. */
  level?: HeadingLevel;
  variant?: keyof typeof HEADING_STYLES;
};

export function Heading({ level = 2, variant = "heading", className, ...props }: HeadingProps) {
  const Tag: ElementType = `h${level}`;
  return (
    <Tag
      className={cn("text-foreground text-balance", HEADING_STYLES[variant], className)}
      {...props}
    />
  );
}

const TEXT_STYLES = {
  "body-lg": "type-body-lg",
  body: "type-body",
  caption: "type-caption",
  overline: "type-overline",
} as const;

const TEXT_TONES = {
  default: "text-foreground",
  muted: "text-muted-foreground",
  subtle: "text-subtle-foreground",
} as const;

export type TextProps = ComponentProps<"p"> & {
  variant?: keyof typeof TEXT_STYLES;
  tone?: keyof typeof TEXT_TONES;
  as?: "p" | "span" | "div";
};

export function Text({
  variant = "body",
  tone = "default",
  as: Tag = "p",
  className,
  ...props
}: TextProps) {
  return <Tag className={cn(TEXT_STYLES[variant], TEXT_TONES[tone], className)} {...props} />;
}
