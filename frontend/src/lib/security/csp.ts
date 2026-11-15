/**
 * Content-Security-Policy for application pages (docs/SECURITY_GUIDELINES.md §7).
 *
 * Scripts: only those carrying this request's nonce, plus what they load ('strict-dynamic').
 * Styles allow inline because React and Radix set `style` attributes; style injection cannot run code.
 * Development adds 'unsafe-eval', which React needs for its debugging stacks; production never does.
 */
export function buildCsp(nonce: string, isDev: boolean): string {
  return [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${isDev ? " 'unsafe-eval'" : ""}`,
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' blob: data:",
    "font-src 'self'",
    "connect-src 'self'",
    "frame-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ].join("; ");
}

/** 128 random bits, base64. A fresh one per page request. */
export function createNonce(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return btoa(String.fromCharCode(...bytes));
}
