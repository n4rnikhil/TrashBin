import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end suite (docs/DEVELOPMENT_ROADMAP.md M6). Starts the backend and the production frontend
 * unless they are already running. Every test registers its own user, so any database works; for a
 * release run, point TRASHBIN_DB_URL / TRASHBIN_STORAGE_ROOT at a fresh database and directory.
 */
const mvnw = process.platform === "win32" ? "mvnw.cmd" : "./mvnw";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: [
    {
      command: `${mvnw} -q spring-boot:run`,
      cwd: "../backend",
      url: "http://localhost:8080/api/v1/health",
      // Every full page load refreshes the session; the default 10/min would throttle the suite.
      env: { TRASHBIN_AUTH_RATE_LIMIT_PER_MINUTE: "1000" },
      reuseExistingServer: true,
      timeout: 180_000,
    },
    {
      command: "npm run build && npm run start",
      url: "http://localhost:3000/login",
      reuseExistingServer: true,
      timeout: 300_000,
    },
  ],
});
