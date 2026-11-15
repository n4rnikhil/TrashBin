import type { Metadata } from "next";
import { FileDetailView } from "@/features/files";

export const metadata: Metadata = { title: "File" };

export default function FilePage() {
  return <FileDetailView />;
}
