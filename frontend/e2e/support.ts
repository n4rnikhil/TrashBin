import { expect, type Locator, type Page } from "@playwright/test";

export const PASSWORD = "correct horse battery staple";

export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
}

/** Creates a fresh account through the UI and lands on the dashboard. */
export async function register(page: Page, prefix = "e2e"): Promise<string> {
  const email = uniqueEmail(prefix);
  await page.goto("/register");
  await page.getByLabel("Name").fill("E2E User");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  return email;
}

/** Client-side navigation through the sidebar (no full reload, so no extra session refresh). */
export async function navigate(page: Page, name: string, path: string): Promise<void> {
  await page.getByRole("link", { name, exact: true }).first().click();
  await expect(page).toHaveURL(new RegExp(`${path}$`));
}

/** Uploads one in-memory text file from the Archive page and opens its detail page. */
export async function uploadText(page: Page, name: string, body: string): Promise<void> {
  await navigate(page, "Archive", "/archive");
  await page
    .locator('input[type="file"]')
    .setInputFiles({ name, mimeType: "text/plain", buffer: Buffer.from(body) });
  await expect(page.getByText(/· Archived$/)).toBeVisible();
  await page.getByRole("link", { name: "View", exact: true }).click();
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
}

/** Presses Tab until `target` has focus — proves it is reachable by keyboard alone. */
export async function tabTo(page: Page, target: Locator, maxPresses = 40): Promise<void> {
  for (let i = 0; i < maxPresses; i++) {
    await page.keyboard.press("Tab");
    if (await target.evaluate((el) => el === document.activeElement)) return;
  }
  throw new Error(`Not reachable with Tab within ${maxPresses} presses`);
}
