import type { Metadata } from "next";
import { PageHeader } from "@/components/patterns/page-header";
import { Card, CardContent } from "@/components/ui/card";
import { TagsManager } from "@/features/tags";

export const metadata: Metadata = { title: "Tags" };

export default function TagsPage() {
  return (
    <>
      <PageHeader
        title="Tags"
        description="Labels that make files easy to find. Rename, recolor or merge them here."
      />
      <Card className="max-w-3xl">
        <CardContent>
          <TagsManager />
        </CardContent>
      </Card>
    </>
  );
}
