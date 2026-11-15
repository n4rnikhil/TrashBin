import type { Metadata } from "next";
import { PageHeader } from "@/components/patterns/page-header";
import { Card, CardContent } from "@/components/ui/card";
import { CollectionsManager } from "@/features/collections";

export const metadata: Metadata = { title: "Collections" };

export default function CollectionsPage() {
  return (
    <>
      <PageHeader
        title="Collections"
        description="Group related files. A file can belong to several collections."
      />
      <Card className="max-w-3xl">
        <CardContent>
          <CollectionsManager />
        </CardContent>
      </Card>
    </>
  );
}
