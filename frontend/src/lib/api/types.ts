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

type Narrowed<T> = Omit<T, "typeGroup" | "status"> & { typeGroup: TypeGroup; status: FileStatus };

export type FileSummary = Narrowed<Nullable<Required<Schemas["FileSummary"]>, "originDate">>;
export type FileDetail = Narrowed<
  Nullable<
    Required<Schemas["FileDetail"]>,
    "notes" | "contextNote" | "originDate" | "lastAccessedAt" | "discardedAt" | "purgeAfter"
  >
>;

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
