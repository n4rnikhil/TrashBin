import { describe, expect, it } from "vitest";
import { buildCsp, createNonce } from "./csp";

describe("csp", () => {
  it("allows only nonce-bearing scripts in production", () => {
    const csp = buildCsp("abc", false);
    expect(csp).toContain("script-src 'self' 'nonce-abc' 'strict-dynamic'");
    expect(csp).not.toContain("unsafe-eval");
    expect(csp).not.toMatch(/script-src[^;]*unsafe-inline/);
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toContain("object-src 'none'");
  });

  it("relaxes eval only in development", () => {
    expect(buildCsp("abc", true)).toContain("'unsafe-eval'");
  });

  it("creates unpredictable nonces", () => {
    const a = createNonce();
    expect(a).toMatch(/^[A-Za-z0-9+/]{22}==$/);
    expect(createNonce()).not.toBe(a);
  });
});
