"use client";

import { usePathname } from "next/navigation";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import { Separator } from "@/components/ui/separator";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { UserMenu } from "@/features/auth";
import { BackendStatusIndicator } from "@/features/status";
import { findNavItem } from "./nav-config";

export function TopNav() {
  const pathname = usePathname();
  const match = findNavItem(pathname);

  return (
    <header className="bg-background/80 supports-backdrop-filter:bg-background/70 sticky top-0 z-20 flex h-14 shrink-0 items-center gap-2 border-b px-3 backdrop-blur md:rounded-t-xl md:px-4">
      <SidebarTrigger className="text-muted-foreground hover:text-foreground -ml-1" />
      <Separator orientation="vertical" className="mr-1 data-vertical:h-4" />
      {match ? (
        <Breadcrumb>
          <BreadcrumbList>
            <BreadcrumbItem className="hidden sm:inline-flex">{match.group.label}</BreadcrumbItem>
            <BreadcrumbSeparator className="hidden sm:block" />
            <BreadcrumbItem>
              <BreadcrumbPage className="font-medium">{match.item.title}</BreadcrumbPage>
            </BreadcrumbItem>
          </BreadcrumbList>
        </Breadcrumb>
      ) : null}
      <div className="ml-auto flex items-center gap-1">
        <BackendStatusIndicator className="hidden sm:inline-flex" />
        <UserMenu />
      </div>
    </header>
  );
}
