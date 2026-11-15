import type { Metadata } from "next";
import { PageHeader } from "@/components/patterns/page-header";
import { Card, CardContent } from "@/components/ui/card";
import { DiscardedList } from "@/features/files";

export const metadata: Metadata = { title: "Discarded" };

export default function DiscardedPage() {
  return (
    <>
      <PageHeader
        title="Discarded"
        description="Files you decided are trash. Open one to restore it or delete it permanently."
      />
      <Card>
        <CardContent>
          <DiscardedList />
        </CardContent>
      </Card>
    </>
  );
}
