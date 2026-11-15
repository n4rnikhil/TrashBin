"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useMemo } from "react";
import { EMPTY_FILTERS, parseFilters, toSearchParams, type FileFilters } from "../filters";

/** Reads the archive filters from the URL and writes changes back as navigation. */
export function useFileFilters() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const filters = useMemo(
    () => parseFilters(new URLSearchParams(searchParams.toString())),
    [searchParams],
  );

  const apply = useCallback(
    (next: FileFilters) => {
      const qs = toSearchParams(next).toString();
      router.push(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
    },
    [router, pathname],
  );

  /** Changing anything but the page goes back to page one. */
  const update = useCallback(
    (patch: Partial<FileFilters>) => apply({ ...filters, ...patch, page: patch.page ?? 0 }),
    [apply, filters],
  );

  const clear = useCallback(
    () => apply({ ...EMPTY_FILTERS, sort: filters.sort }),
    [apply, filters],
  );

  return { filters, update, clear };
}
