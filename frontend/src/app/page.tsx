import Link from "next/link";
import { Button } from "@/components/ui/button";

export default function HomePage() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-6 p-8 text-center">
      <h1 className="text-4xl font-semibold tracking-tight">Trash Bin</h1>
      <p className="text-muted-foreground max-w-md">
        Stores your trash. Nothing is truly trash until you decide it is.
      </p>
      <Button render={<Link href="/status" />}>System status</Button>
    </main>
  );
}
