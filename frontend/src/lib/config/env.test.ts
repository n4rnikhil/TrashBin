import { describe, expect, it } from "vitest";
import { parseServerEnv } from "./env";

describe("parseServerEnv", () => {
  it("defaults BACKEND_URL to the local backend", () => {
    expect(parseServerEnv({}).BACKEND_URL).toBe("http://localhost:8080");
  });

  it("treats an empty value as unset", () => {
    expect(parseServerEnv({ BACKEND_URL: "" }).BACKEND_URL).toBe("http://localhost:8080");
  });

  it("strips trailing slashes", () => {
    expect(parseServerEnv({ BACKEND_URL: "http://backend:9000//" }).BACKEND_URL).toBe(
      "http://backend:9000",
    );
  });

  it("rejects values that are not URLs", () => {
    expect(() => parseServerEnv({ BACKEND_URL: "not a url" })).toThrow(/BACKEND_URL/);
  });

  it("rejects non-http schemes", () => {
    expect(() => parseServerEnv({ BACKEND_URL: "ftp://example.com" })).toThrow(/BACKEND_URL/);
  });

  it("never echoes the offending value", () => {
    expect(() => parseServerEnv({ BACKEND_URL: "super-secret-garbage" })).not.toThrow(
      /super-secret-garbage/,
    );
    try {
      parseServerEnv({ BACKEND_URL: "super-secret-garbage" });
    } catch (error) {
      expect(String(error)).not.toContain("super-secret-garbage");
    }
  });
});
