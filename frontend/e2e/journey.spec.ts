import { expect, test } from "@playwright/test";
import { navigate, register, uploadText } from "./support";

test("archive → find → preview → download → discard → restore → discard → purge", async ({
  page,
}) => {
  await register(page, "journey");
  const marker = `zebracrossing${Date.now()}`;
  await uploadText(page, "old-lease.txt", `Apartment lease, signed ${marker}.`);

  // Preview renders the text as text.
  await expect(page.getByText(`Apartment lease, signed ${marker}.`)).toBeVisible();

  // Download goes through a short-lived ticket.
  const [download] = await Promise.all([
    page.waitForEvent("download"),
    page.getByRole("button", { name: "Download" }).click(),
  ]);
  expect(download.suggestedFilename()).toBe("old-lease.txt");

  // Integrity check passes on an untouched file.
  await page.getByRole("button", { name: "Verify integrity" }).click();
  await expect(page.getByText("Intact — matches the original checksum")).toBeVisible();

  // Found by name search from the archive.
  await navigate(page, "Archive", "/archive");
  const search = page.getByRole("main").getByRole("searchbox", { name: "Search your archive" });
  await search.fill("old-lease");
  await search.press("Enter");
  await expect(page.getByRole("link", { name: /old-lease/ })).toBeVisible();
  await page.getByRole("link", { name: /old-lease/ }).click();

  // Discard → restorable from the Discarded view.
  await page.getByRole("button", { name: "Discard" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Discard" }).click();
  await expect(page.getByText(/will be deleted permanently on/)).toBeVisible();

  await navigate(page, "Discarded", "/discarded");
  await page.getByRole("link", { name: /old-lease/ }).click();
  await page.getByRole("button", { name: "Restore" }).click();
  await expect(page.getByRole("button", { name: "Discard" })).toBeVisible();

  // Discard again, then delete for good.
  await page.getByRole("button", { name: "Discard" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Discard" }).click();
  await page.getByRole("button", { name: "Delete permanently" }).click();
  const confirm = page.getByRole("dialog");
  await expect(confirm.getByText(/cannot be undone/)).toBeVisible();
  await confirm.getByRole("button", { name: "Delete permanently" }).click();

  await expect(page).toHaveURL(/\/discarded$/);
  await expect(page.getByText("Nothing discarded")).toBeVisible();
  await navigate(page, "Archive", "/archive");
  await expect(page.getByRole("link", { name: /old-lease/ })).toHaveCount(0);
});

test("a wrong password is refused without revealing why", async ({ page }) => {
  const email = await register(page, "badlogin");
  await page.getByRole("button", { name: /Account menu for/ }).click();
  await page.getByRole("menuitem", { name: "Sign out" }).click();

  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill("definitely not it");
  await page.getByRole("button", { name: "Sign in" }).click();
  // Filtered: Next's route announcer is also an alert region.
  await expect(
    page.getByRole("alert").filter({ hasText: "The email or password is incorrect." }),
  ).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);
});

test("a reload keeps the session through the refresh cookie", async ({ page }) => {
  await register(page, "reload");
  await page.reload();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: "Overview" })).toBeVisible();
});

test("signing out ends the session", async ({ page }) => {
  await register(page, "signout");
  await page.getByRole("button", { name: /Account menu for/ }).click();
  await page.getByRole("menuitem", { name: "Sign out" }).click();
  await expect(page).toHaveURL(/\/login$/);

  await page.goto("/dashboard");
  await expect(page).toHaveURL(/\/login$/);
});
