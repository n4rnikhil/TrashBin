import type { Metadata } from "next";
import { BackendStatusCard } from "@/features/status";

export const metadata: Metadata = { title: "Status · Trash Bin" };

export default function StatusPage() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-6 p-8">
      <BackendStatusCard />
    </main>
  );
}
