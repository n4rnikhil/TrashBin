import type { Metadata } from "next";
import { Suspense } from "react";
import { PageHeader } from "@/components/patterns/page-header";
import { ListSkeleton } from "@/components/patterns/skeletons";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { FileList, UploadPanel } from "@/features/files";

export const metadata: Metadata = { title: "Archive" };

export default function ArchivePage() {
  return (
    <>
      <PageHeader
        title="Archive"
        description="Everything you have kept. Originals are stored untouched with their checksum."
      />
      <Card>
        <CardContent>
          <UploadPanel />
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Files</CardTitle>
        </CardHeader>
        <CardContent>
          {/* FileList reads page/sort from the URL, which requires a Suspense boundary. */}
          <Suspense fallback={<ListSkeleton rows={6} />}>
            <FileList />
          </Suspense>
        </CardContent>
      </Card>
    </>
  );
}
