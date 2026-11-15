import { describe, expect, it } from "vitest";
import { EMPTY_FILTERS, effectiveSort, parseFilters, toSearchParams } from "./filters";

const TAG = "0b6c2f6e-2f5c-4c52-9a1e-1b8f2a1c9d10";
const COLLECTION = "5f0e7a7d-3c2a-4b1e-8f6d-7e9c0a1b2c3d";

describe("file filters in the URL", () => {
  it("round-trips every filter", () => {
    const filters = {
      ...EMPTY_FILTERS,
      q: "lease",
      tags: [TAG],
      collection: COLLECTION,
      typeGroup: "DOCUMENT" as const,
      from: "2021-01-01",
      to: "2021-12-31",
      favorite: true,
      sort: "displayName,asc" as const,
      page: 2,
    };

    expect(parseFilters(toSearchParams(filters))).toEqual(filters);
  });

  it("leaves defaults out of the URL", () => {
    expect(toSearchParams(EMPTY_FILTERS).toString()).toBe("");
  });

  it("ignores malformed values instead of failing", () => {
    const parsed = parseFilters(
      new URLSearchParams(
        "tag=nope&collection=1&type=SPACESHIP&from=yesterday&sort=sha256&page=-4",
      ),
    );

    expect(parsed).toEqual(EMPTY_FILTERS);
  });

  it("defaults to best match only while searching", () => {
    expect(effectiveSort(EMPTY_FILTERS)).toBe("archivedAt,desc");
    expect(effectiveSort({ ...EMPTY_FILTERS, q: "x" })).toBe("relevance");
    expect(effectiveSort({ ...EMPTY_FILTERS, sort: "relevance" })).toBe("archivedAt,desc");
    expect(effectiveSort({ ...EMPTY_FILTERS, q: "x", sort: "sizeBytes,desc" })).toBe(
      "sizeBytes,desc",
    );
  });
});
