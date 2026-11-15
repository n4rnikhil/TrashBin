import {
  Activity,
  Archive,
  LayoutDashboard,
  Settings,
  Library,
  type LucideIcon,
  Sparkles,
  Tags,
  Trash2,
} from "lucide-react";

/**
 * Navigation model. `planned` sections are shown so the product shape is visible, but they render
 * as disabled entries (never links) until their milestone ships: no fake features.
 */
export type NavItem = {
  title: string;
  href: string;
  icon: LucideIcon;
} & ({ status: "available" } | { status: "planned"; milestone: string });

export type NavGroup = { label: string; items: readonly NavItem[] };

export const NAV_GROUPS: readonly NavGroup[] = [
  {
    label: "Workspace",
    items: [
      { title: "Overview", href: "/dashboard", icon: LayoutDashboard, status: "available" },
      { title: "Archive", href: "/archive", icon: Archive, status: "available" },
      { title: "Collections", href: "/collections", icon: Library, status: "available" },
      { title: "Tags", href: "/tags", icon: Tags, status: "available" },
      {
        title: "Rediscover",
        href: "/rediscover",
        icon: Sparkles,
        status: "planned",
        milestone: "M5",
      },
      { title: "Discarded", href: "/discarded", icon: Trash2, status: "planned", milestone: "M5" },
    ],
  },
  {
    label: "System",
    items: [
      { title: "Status", href: "/status", icon: Activity, status: "available" },
      { title: "Settings", href: "/settings", icon: Settings, status: "available" },
    ],
  },
];

/** Finds the nav entry for a pathname (exact match or nested route). */
export function findNavItem(pathname: string): { group: NavGroup; item: NavItem } | undefined {
  // File detail pages live under the Archive section.
  if (pathname.startsWith("/files/")) return findNavItem("/archive");
  for (const group of NAV_GROUPS) {
    for (const item of group.items) {
      if (pathname === item.href || pathname.startsWith(`${item.href}/`)) return { group, item };
    }
  }
  return undefined;
}
