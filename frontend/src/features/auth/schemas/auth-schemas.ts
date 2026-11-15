import { z } from "zod";

/**
 * Client-side mirrors of the server's rules, for immediate feedback only. The backend remains the
 * authority (docs/SECURITY_GUIDELINES.md 3.1): min 12 characters, max 72 bytes in UTF-8.
 */
const utf8Length = (value: string) => new TextEncoder().encode(value).length;

export const passwordSchema = z
  .string()
  .min(12, "Use at least 12 characters.")
  .refine((value) => utf8Length(value) <= 72, "Password is too long (72 bytes maximum).");

export const loginSchema = z.object({
  email: z.email("Enter a valid email address."),
  password: z.string().min(1, "Enter your password."),
});
export type LoginValues = z.infer<typeof loginSchema>;

export const registerSchema = z.object({
  displayName: z.string().trim().min(1, "Enter your name.").max(100, "Name is too long."),
  email: z.email("Enter a valid email address.").max(254),
  password: passwordSchema,
});
export type RegisterValues = z.infer<typeof registerSchema>;

export const profileSchema = z.object({
  displayName: z.string().trim().min(1, "Enter your name.").max(100, "Name is too long."),
});
export type ProfileValues = z.infer<typeof profileSchema>;

export const changePasswordSchema = z.object({
  currentPassword: z.string().min(1, "Enter your current password."),
  newPassword: passwordSchema,
});
export type ChangePasswordValues = z.infer<typeof changePasswordSchema>;
