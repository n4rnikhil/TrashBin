"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from "@/components/ui/sidebar";
import { BrandLockup } from "./brand";
import { NAV_GROUPS, type NavItem } from "./nav-config";

function NavEntry({ item, active }: { item: NavItem; active: boolean }) {
  const Icon = item.icon;

  if (item.status === "planned") {
    return (
      <SidebarMenuItem>
        <SidebarMenuButton
          aria-disabled
          tabIndex={-1}
          tooltip={`${item.title} — coming in ${item.milestone}`}
          className="text-subtle-foreground hover:text-subtle-foreground cursor-default hover:bg-transparent"
        >
          <Icon />
          <span>{item.title}</span>
        </SidebarMenuButton>
        <SidebarMenuBadge className="text-subtle-foreground bg-sidebar-accent/60 type-overline rounded-md px-1.5 text-[0.625rem]">
          Soon
        </SidebarMenuBadge>
      </SidebarMenuItem>
    );
  }

  return (
    <SidebarMenuItem>
      <SidebarMenuButton
        isActive={active}
        tooltip={item.title}
        render={<Link href={item.href} aria-current={active ? "page" : undefined} />}
        className="data-active:bg-card data-active:text-foreground data-active:ring-border data-active:[&_svg]:text-primary data-active:font-medium data-active:shadow-xs data-active:ring-1 [&_svg]:transition-colors"
      >
        <Icon />
        <span>{item.title}</span>
      </SidebarMenuButton>
    </SidebarMenuItem>
  );
}

export function AppSidebar() {
  const pathname = usePathname();

  return (
    <Sidebar variant="inset" collapsible="icon">
      <SidebarHeader className="px-2 pt-3 pb-2">
        <Link
          href="/dashboard"
          className="rounded-lg p-1 transition-opacity group-data-[collapsible=icon]:p-0 hover:opacity-90"
          aria-label="Trash Bin home"
        >
          <BrandLockup className="group-data-[collapsible=icon]:[&>span:last-child]:hidden" />
        </Link>
      </SidebarHeader>

      <SidebarContent>
        {NAV_GROUPS.map((group) => (
          <SidebarGroup key={group.label}>
            <SidebarGroupLabel className="type-overline text-subtle-foreground">
              {group.label}
            </SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {group.items.map((item) => (
                  <NavEntry
                    key={item.href}
                    item={item}
                    active={pathname === item.href || pathname.startsWith(`${item.href}/`)}
                  />
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        ))}
      </SidebarContent>

      <SidebarFooter className="px-3 pb-4 group-data-[collapsible=icon]:hidden">
        <p className="text-subtle-foreground type-caption font-normal text-pretty whitespace-normal">
          Nothing is truly trash until you decide it is.
        </p>
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}
