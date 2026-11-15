/**
 * ============================================================================
 *  STATIC DESIGN FIXTURES — NOT REAL DATA
 * ============================================================================
 * Used only by the development style guide at /design to preview layouts.
 * ESLint forbids importing anything under src/dev from application code
 * (src/app outside (dev), src/features, src/components, src/lib).
 */

export type SampleFile = {
  id: string;
  name: string;
  typeGroup: "DOCUMENT" | "IMAGE" | "SPREADSHEET" | "ARCHIVE";
  sizeLabel: string;
  archivedLabel: string;
  tags: readonly string[];
};

export const SAMPLE_FILES: readonly SampleFile[] = [
  {
    id: "sample-1",
    name: "Apartment lease 2021.pdf",
    typeGroup: "DOCUMENT",
    sizeLabel: "1.2 MB",
    archivedLabel: "Archived 3 days ago",
    tags: ["housing", "contracts"],
  },
  {
    id: "sample-2",
    name: "Lisbon trip.jpg",
    typeGroup: "IMAGE",
    sizeLabel: "4.8 MB",
    archivedLabel: "Archived last week",
    tags: ["travel"],
  },
  {
    id: "sample-3",
    name: "Budget Q3.xlsx",
    typeGroup: "SPREADSHEET",
    sizeLabel: "86 KB",
    archivedLabel: "Archived in March",
    tags: ["finance", "work"],
  },
];
