import { Archive, Flame, Trash2, type LucideIcon } from "lucide-react";
import type { Metadata } from "next";
import { EmptyState } from "@/components/patterns/empty-state";
import { PageHeader } from "@/components/patterns/page-header";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Text } from "@/components/ui/typography";
import { BackendStatusCard } from "@/features/status";

export const metadata: Metadata = { title: "Overview" };

/** Product explanation (static copy, not data). Mirrors the lifecycle in PROJECT_REQUIREMENTS. */
const LIFECYCLE: readonly { icon: LucideIcon; title: string; description: string }[] = [
  {
    icon: Archive,
    title: "Archive",
    description: "Keep the original file with your notes, tags and the reason you kept it.",
  },
  {
    icon: Trash2,
    title: "Discard",
    description: "Decide it is truly trash. It stays restorable during the retention window.",
  },
  {
    icon: Flame,
    title: "Purge",
    description: "Delete permanently, only when you are sure. This cannot be undone.",
  },
];

export default function DashboardPage() {
  return (
    <>
      <PageHeader
        title="Overview"
        description="Everything you have chosen to keep, in one place."
      />

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Your archive</CardTitle>
            <CardDescription>Recently archived files will appear here.</CardDescription>
          </CardHeader>
          <CardContent>
            <EmptyState
              icon={Archive}
              title="Your archive is empty"
              description="Uploading arrives in the next milestone. Files you archive will be preserved, searchable and restorable."
            />
          </CardContent>
        </Card>

        <div className="flex flex-col gap-6">
          <BackendStatusCard />
          <Card>
            <CardHeader>
              <CardTitle>How Trash Bin works</CardTitle>
              <CardDescription>Nothing is truly trash until you decide it is.</CardDescription>
            </CardHeader>
            <CardContent>
              <ol className="flex flex-col gap-4">
                {LIFECYCLE.map(({ icon: Icon, title, description }, index) => (
                  <li key={title} className="flex gap-3">
                    <span className="bg-accent text-accent-foreground flex size-8 shrink-0 items-center justify-center rounded-lg">
                      <Icon className="size-4" aria-hidden />
                    </span>
                    <div className="flex flex-col gap-0.5">
                      <Text as="span" variant="caption" tone="subtle">
                        Step {index + 1}
                      </Text>
                      <Text as="span" className="font-medium">
                        {title}
                      </Text>
                      <Text as="span" tone="muted" className="type-caption font-normal">
                        {description}
                      </Text>
                    </div>
                  </li>
                ))}
              </ol>
            </CardContent>
          </Card>
        </div>
      </div>
    </>
  );
}
