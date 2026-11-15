import { describe, expect, it } from "vitest";
import { formatBytes, formatCalendarDate } from "./format";

describe("formatBytes", () => {
  it.each([
    [0, "0 B"],
    [999, "999 B"],
    [1536, "1.5 KB"],
    [10 * 1024, "10 KB"],
    [5 * 1024 ** 3, "5.0 GB"],
    [-1, "—"],
  ])("formats %d as %s", (bytes, expected) => {
    expect(formatBytes(bytes)).toBe(expected);
  });
});

describe("formatCalendarDate", () => {
  it("does not shift the day across time zones", () => {
    expect(formatCalendarDate("2020-01-01", "en-GB")).toBe("1 Jan 2020");
  });

  it("returns malformed input unchanged", () => {
    expect(formatCalendarDate("not-a-date")).toBe("not-a-date");
  });
});
