import type { components } from "./generated/schema";

type Schemas = components["schemas"];

/**
 * App-facing API types derived from the generated OpenAPI schema. The generator marks response
 * properties optional (Java records carry no "required" metadata), but the backend always sends
 * them, so responses are narrowed to required here, in one place.
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
