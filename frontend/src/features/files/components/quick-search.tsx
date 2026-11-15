"use client";

import { Search } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Input } from "@/components/ui/input";

/** Top-bar search: Enter opens the archive with the query. */
export function QuickSearch({ className }: { className?: string }) {
  const router = useRouter();
  const [q, setQ] = useState("");

  return (
    <form
      role="search"
      className={className}
      onSubmit={(event) => {
        event.preventDefault();
        const text = q.trim();
        if (!text) return;
        router.push(`/archive?q=${encodeURIComponent(text)}`);
        setQ("");
      }}
    >
      <div className="relative">
        <Search
          className="text-muted-foreground pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2"
          aria-hidden
        />
        <Input
          type="search"
          aria-label="Search your archive"
          placeholder="Search…"
          className="h-8 w-56 pl-8"
          maxLength={200}
          value={q}
          onChange={(event) => setQ(event.target.value)}
        />
      </div>
    </form>
  );
}
