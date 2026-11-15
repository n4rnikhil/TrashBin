import { redirect } from "next/navigation";

/** The app has no public landing page yet; the workspace overview is the entry point. */
export default function RootPage() {
  redirect("/dashboard");
}
