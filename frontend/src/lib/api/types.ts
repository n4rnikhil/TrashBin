import type { components } from "./generated/schema";

type Schemas = components["schemas"];

/** Marks keys the backend may send as JSON null (Java records serialize absent values as null). */
type Nullable<T, K extends keyof T> = Omit<T, K> & { [P in K]: T[P] | null };

/**
 * App-facing API types derived from the generated OpenAPI schema. The generator marks response
 * properties optional (Java records carry no "required" metadata), but the backend always sends
 * them, so responses are narrowed here, in one place.
 */
export type UserProfile = Required<Schemas["UserProfile"]>;
export type AuthResponse = Required<Omit<Schemas["AuthResponse"], "user">> & { user: UserProfile };
export type SessionInfo = Required<Omit<Schemas["SessionInfo"], "userAgent">> & {
  userAgent?: string;
};
export type HealthResponse = Schemas["HealthResponse"];

export type LoginRequest = Schemas["LoginRequest"];
export type RegisterRequest = Schemas["RegisterRequest"];
export type ChangePasswordRequest = Schemas["ChangePasswordRequest"];
export type UploadMetadata = Schemas["UploadMetadata"];

/** Enumerations documented in docs/API_CONTRACT.md (sent as strings). */
export type TypeGroup =
  | "IMAGE"
  | "DOCUMENT"
  | "SPREADSHEET"
  | "PRESENTATION"
  | "TEXT"
  | "AUDIO"
  | "VIDEO"
  | "ARCHIVE"
  | "OTHER";
export type FileStatus = "ARCHIVED" | "DISCARDED";
/** Progress of text extraction for content search (M4b). */
export type ExtractionStatus = "PENDING" | "DONE" | "SKIPPED" | "FAILED";

export type TagRef = Nullable<Required<Schemas["TagRef"]>, "color">;
export type TagSummary = Nullable<Required<Schemas["TagSummary"]>, "color">;
export type CreateTagRequest = Schemas["CreateTagRequest"];
export type UpdateTagRequest = Schemas["UpdateTagRequest"];
/** Partial edit: omitted fields stay, "" clears notes/contextNote/originDate. */
export type UpdateFileRequest = Schemas["UpdateFileRequest"];

/** A short-lived link to a file's bytes; `url` is relative to this site. */
export type AccessTicket = Required<Schemas["AccessTicket"]>;
export type TicketPurpose = "DOWNLOAD" | "PREVIEW";

export type CollectionRef = Required<Schemas["CollectionRef"]>;
export type CollectionSummary = Nullable<Required<Schemas["CollectionSummary"]>, "description">;
export type CreateCollectionRequest = Schemas["CreateCollectionRequest"];
export type UpdateCollectionRequest = Schemas["UpdateCollectionRequest"];

type Narrowed<T> = Omit<T, "typeGroup" | "status" | "tags"> & {
  typeGroup: TypeGroup;
  status: FileStatus;
  tags: TagRef[];
};

export type FileSummary = Narrowed<
  Nullable<Required<Schemas["FileSummary"]>, "originDate" | "purgeAfter">
>;

/** Overview numbers; `retentionDays` 0 means discarded files are never purged automatically. */
export type Stats = Required<Schemas["Stats"]>;
/** Result of re-hashing the stored bytes; `actualSha256` is null when the bytes are missing. */
export type IntegrityResult = Omit<Required<Schemas["IntegrityResult"]>, "actualSha256"> & {
  actualSha256: string | null;
};
export type FileEventType =
  | "ARCHIVED"
  | "METADATA_EDITED"
  | "DOWNLOADED"
  | "PREVIEWED"
  | "DISCARDED"
  | "RESTORED"
  | "INTEGRITY_VERIFIED";
export type FileEvent = Omit<Required<Schemas["FileEventView"]>, "type"> & { type: FileEventType };
export type RediscoverMode = "on-this-day" | "forgotten" | "random";
export type FileDetail = Narrowed<
  Nullable<
    Omit<Required<Schemas["FileDetail"]>, "collections" | "extractionStatus">,
    "notes" | "contextNote" | "originDate" | "lastAccessedAt" | "discardedAt" | "purgeAfter"
  >
> & { collections: CollectionRef[]; extractionStatus: ExtractionStatus };

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
