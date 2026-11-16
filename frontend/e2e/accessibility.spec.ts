import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { navigate, PASSWORD, register, tabTo, uploadText } from "./support";

const WCAG = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"];

async function expectNoSeriousViolations(page: Page, screen: string) {
  // Let client-side data settle so axe sees the real screen, not a skeleton.
  await page.waitForLoadState("networkidle");
  const { violations } = await new AxeBuilder({ page }).withTags(WCAG).analyze();
  const serious = violations
    .filter((v) => v.impact === "serious" || v.impact === "critical")
    .map(
      (v) =>
        `${v.id} (${v.impact}): ${v.nodes
          .map((n) => `${n.html.slice(0, 120)} → ${n.any[0]?.message ?? n.failureSummary ?? ""}`)
          .join(" | ")}`,
    );
  expect(serious, `${screen}: ${serious.join("\n")}`).toEqual([]);
}

async function expectNoHorizontalScroll(page: Page, screen: string) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow, `${screen} scrolls sideways at this width`).toBeLessThanOrEqual(0);
}

const SCREENS: [name: string, path: string][] = [
  ["Overview", "/dashboard"],
  ["Archive", "/archive"],
  ["Collections", "/collections"],
  ["Tags", "/tags"],
  ["Rediscover", "/rediscover"],
  ["Discarded", "/discarded"],
  ["Settings", "/settings"],
];

for (const width of [1280, 360]) {
  test(`core screens: axe clean and no sideways scroll at ${width}px`, async ({ page }) => {
    test.setTimeout(180_000); // eleven screens, each analysed by axe
    await page.setViewportSize({ width, height: 800 });

    for (const path of ["/login", "/register"]) {
      await page.goto(path);
      await expectNoSeriousViolations(page, path);
      await expectNoHorizontalScroll(page, path);
    }

    await register(page, `a11y${width}`);
    if (width < 768) {
      // On phones the sidebar is a sheet; only the file detail needs a link click from the archive.
      await uploadTextMobile(page);
    } else {
      await uploadText(page, "notes.txt", "Accessibility fixture.");
    }
    await expectNoSeriousViolations(page, "file detail");
    await expectNoHorizontalScroll(page, "file detail");

    // Discarded state: warning badge, banner with countdown, and a non-empty Discarded list below.
    await page.getByRole("button", { name: "Discard" }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Discard" }).click();
    await expect(page.getByText(/will be deleted permanently on/)).toBeVisible();
    await expectNoSeriousViolations(page, "discarded file detail");
    await expectNoHorizontalScroll(page, "discarded file detail");

    for (const [name, path] of SCREENS) {
      await openScreen(page, name, path, width);
      await expectNoSeriousViolations(page, path);
      await expectNoHorizontalScroll(page, path);
    }
  });
}

async function openScreen(page: Page, name: string, path: string, width: number) {
  if (width < 768) {
    await page.getByRole("button", { name: "Toggle Sidebar" }).click();
  }
  await navigate(page, name, path);
  if (width < 768) {
    // The sheet may already be closing after navigation; wait until it is fully gone so axe
    // never measures text mid-fade.
    await page.keyboard.press("Escape");
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await page.waitForTimeout(400);
  }
}

async function uploadTextMobile(page: Page) {
  await openScreen(page, "Archive", "/archive", 360);
  await page.locator('input[type="file"]').setInputFiles({
    name: "notes.txt",
    mimeType: "text/plain",
    buffer: Buffer.from("Accessibility fixture."),
  });
  await expect(page.getByText(/· Archived$/)).toBeVisible();
  await page.getByRole("link", { name: "View", exact: true }).click();
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
}

test("keyboard only: sign in, reach the archive, open and dismiss a dialog", async ({ page }) => {
  const email = await register(page, "keyboard");
  await page.getByRole("button", { name: /Account menu for/ }).click();
  await page.getByRole("menuitem", { name: "Sign out" }).click();
  await expect(page).toHaveURL(/\/login$/);

  await tabTo(page, page.getByLabel("Email"));
  await page.keyboard.type(email);
  await tabTo(page, page.getByLabel("Password"));
  await page.keyboard.type(PASSWORD);
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/\/dashboard$/);

  await tabTo(page, page.getByRole("link", { name: "Archive", exact: true }).first());
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/\/archive$/);

  await tabTo(page, page.getByRole("button", { name: "Choose files" }));
  await uploadText(page, "kbd.txt", "Keyboard fixture.");

  const discard = page.getByRole("button", { name: "Discard" });
  await tabTo(page, discard);
  await page.keyboard.press("Enter");
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(discard).toBeFocused();
});
