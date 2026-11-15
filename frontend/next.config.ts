import type { NextConfig } from "next";
import { parseServerEnv } from "./src/lib/config/env";

// Fail fast at startup/build if the environment is invalid (see src/lib/config/env.ts).
const { BACKEND_URL } = parseServerEnv(process.env);

const nextConfig: NextConfig = {
  // No Cache Components / partial prerendering: they cannot carry the per-request CSP nonce
  // (src/proxy.ts), and authenticated pages render client-side anyway.
  turbopack: {
    // Pin the workspace root so a stray lockfile in a parent folder is never picked up.
    root: process.cwd(),
    rules: {
      "*.css": {
        // Only our own stylesheets: Next internals (e.g. next/font CSS) must not go through Tailwind.
        condition: { not: "foreign" },
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  // The browser only ever talks to this origin; /api/* is proxied to the Spring Boot backend so the
  // refresh cookie is first-party and no CORS setup is needed (docs/SYSTEM_ARCHITECTURE.md 3.3).
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${BACKEND_URL}/api/:path*` }];
  },
  // Baseline headers for pages. The nonce-based CSP is set per request in src/proxy.ts.
  async headers() {
    return [
      {
        source: "/((?!api).*)",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "no-referrer" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
        ],
      },
    ];
  },
};

export default nextConfig;
