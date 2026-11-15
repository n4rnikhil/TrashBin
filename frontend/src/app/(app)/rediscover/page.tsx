import type { Metadata } from "next";
import { PageHeader } from "@/components/patterns/page-header";
import { RediscoverView } from "@/features/files";

export const metadata: Metadata = { title: "Rediscover" };

export default function RediscoverPage() {
  return (
    <>
      <PageHeader title="Rediscover" description="Things you kept and may have forgotten about." />
      <RediscoverView />
    </>
  );
}
