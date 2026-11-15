import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DesignShowcase } from "./_components/design-showcase";

export const metadata: Metadata = { title: "Design system", robots: { index: false } };

/** Development-only style guide. Returns 404 in production builds. */
export default function DesignPage() {
  if (process.env.NODE_ENV === "production") notFound();
  return <DesignShowcase />;
}
