"use client";

import { Star } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { errorMessage } from "@/lib/api/errors";
import { cn } from "@/lib/utils";
import { useFileMutations } from "../hooks/use-file-mutations";

export function FavoriteButton({ fileId, favorite }: { fileId: string; favorite: boolean }) {
  const { setFavorite } = useFileMutations(fileId);
  return (
    <Button
      variant="outline"
      aria-pressed={favorite}
      disabled={setFavorite.isPending}
      onClick={() =>
        setFavorite.mutate(!favorite, { onError: (error) => toast.error(errorMessage(error)) })
      }
    >
      <Star className={cn(favorite && "fill-warning text-warning")} aria-hidden />
      {favorite ? "Favorite" : "Add to favorites"}
    </Button>
  );
}
