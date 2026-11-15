import { parseServerEnv } from "@/lib/config/env";

/** Runs once when the Next.js server starts; aborts startup on invalid configuration. */
export function register(): void {
  parseServerEnv(process.env);
}
