import { z } from "zod";

/**
 * Server-side environment contract. Validated once at startup (next.config.ts and
 * instrumentation.ts) so misconfiguration fails fast with a readable message.
 *
 * Nothing here is ever exposed to the browser; do not use NEXT_PUBLIC_* for anything sensitive.
 */
const serverEnvSchema = z.object({
  /** Base URL of the Spring Boot backend, target of the /api/* rewrite. */
  BACKEND_URL: z
    .url({ protocol: /^https?$/, error: "BACKEND_URL must be an http(s) URL" })
    .default("http://localhost:8080")
    .transform((value) => value.replace(/\/+$/, "")),
});

export type ServerEnv = z.infer<typeof serverEnvSchema>;

export function parseServerEnv(source: Record<string, string | undefined>): ServerEnv {
  const result = serverEnvSchema.safeParse({ BACKEND_URL: source["BACKEND_URL"] || undefined });
  if (!result.success) {
    // Report which variables are wrong, never their values.
    const problems = result.error.issues
      .map((issue) => `${issue.path.join(".") || "env"}: ${issue.message}`)
      .join("; ");
    throw new Error(`Invalid environment configuration: ${problems}`);
  }
  return result.data;
}
