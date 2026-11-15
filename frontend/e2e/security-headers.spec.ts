import { expect, test } from "@playwright/test";
import { register } from "./support";

test("pages carry a fresh nonce CSP and nothing violates it", async ({ page }) => {
  const violations: string[] = [];
  page.on("console", (msg) => {
    if (/Content Security Policy|Refused to/i.test(msg.text())) violations.push(msg.text());
  });
  page.on("pageerror", (err) => violations.push(err.message));

  const first = await page.goto("/login");
  const second = await page.request.get("/login");
  const csp = first?.headers()["content-security-policy"] ?? "";
  expect(csp).toMatch(/script-src 'self' 'nonce-[A-Za-z0-9+/=]+' 'strict-dynamic'/);
  expect(csp).toContain("frame-ancestors 'none'");
  expect(csp).not.toContain("unsafe-eval");
  expect(second.headers()["content-security-policy"]).not.toBe(csp);

  const headers = first?.headers() ?? {};
  expect(headers["x-content-type-options"]).toBe("nosniff");
  expect(headers["referrer-policy"]).toBe("no-referrer");

  // Full app shell renders and hydrates under the policy.
  await register(page, "csp");
  await expect(page.getByRole("heading", { name: "Overview" })).toBeVisible();
  expect(violations).toEqual([]);
});
