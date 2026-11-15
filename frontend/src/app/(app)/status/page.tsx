import type { Metadata } from "next";
import { PageHeader } from "@/components/patterns/page-header";
import { BackendStatusCard } from "@/features/status";

export const metadata: Metadata = { title: "Status" };

export default function StatusPage() {
  return (
    <>
      <PageHeader
        title="System status"
        description="Live health of the backend and its database, checked through the application proxy."
      />
      <BackendStatusCard className="max-w-xl" />
    </>
  );
}
