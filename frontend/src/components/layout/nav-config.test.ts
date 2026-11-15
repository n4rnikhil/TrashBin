import { describe, expect, it } from "vitest";
import { findNavItem, NAV_GROUPS } from "./nav-config";

describe("nav-config", () => {
  it("resolves exact and nested routes", () => {
    expect(findNavItem("/dashboard")?.item.title).toBe("Overview");
    expect(findNavItem("/status")?.group.label).toBe("System");
    expect(findNavItem("/nowhere")).toBeUndefined();
  });

  it("marks every not-yet-built section as planned with a milestone", () => {
    const items = NAV_GROUPS.flatMap((group) => group.items);
    const available = items.filter((item) => item.status === "available").map((i) => i.href);
    // Only routes that really exist may be navigable.
    expect(available).toEqual(["/dashboard", "/status"]);
    for (const item of items) {
      if (item.status === "planned") expect(item.milestone).toMatch(/^M\d$/);
    }
  });
});
