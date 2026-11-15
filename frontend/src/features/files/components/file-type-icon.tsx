import {
  File,
  FileArchive,
  FileCode,
  FileText,
  Film,
  Image,
  type LucideIcon,
  Music,
  Presentation,
  Sheet,
} from "lucide-react";
import type { TypeGroup } from "@/lib/api/types";
import { cn } from "@/lib/utils";

const ICONS: Record<TypeGroup, LucideIcon> = {
  IMAGE: Image,
  DOCUMENT: FileText,
  SPREADSHEET: Sheet,
  PRESENTATION: Presentation,
  TEXT: FileCode,
  AUDIO: Music,
  VIDEO: Film,
  ARCHIVE: FileArchive,
  OTHER: File,
};

export const TYPE_LABELS: Record<TypeGroup, string> = {
  IMAGE: "Image",
  DOCUMENT: "Document",
  SPREADSHEET: "Spreadsheet",
  PRESENTATION: "Presentation",
  TEXT: "Text",
  AUDIO: "Audio",
  VIDEO: "Video",
  ARCHIVE: "Archive",
  OTHER: "File",
};

export function FileTypeIcon({
  typeGroup,
  size = "md",
  className,
}: {
  typeGroup: TypeGroup;
  size?: "md" | "lg";
  className?: string;
}) {
  const Icon = ICONS[typeGroup] ?? File;
  return (
    <span
      className={cn(
        "bg-accent text-accent-foreground flex shrink-0 items-center justify-center rounded-lg",
        size === "lg" ? "size-12" : "size-9",
        className,
      )}
      aria-hidden
    >
      <Icon className={size === "lg" ? "size-6" : "size-4"} strokeWidth={1.75} />
    </span>
  );
}
