import { NextResponse, type NextRequest } from "next/server";
import { buildCsp, createNonce } from "@/lib/security/csp";

/**
 * Per-request nonce CSP for pages. Next.js reads the nonce from the request's CSP header and stamps
 * it on its own scripts, which is why every page renders dynamically (see app/layout.tsx).
 */
export function proxy(request: NextRequest) {
  const csp = buildCsp(createNonce(), process.env.NODE_ENV === "development");

  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("Content-Security-Policy", csp);

  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set("Content-Security-Policy", csp);
  return response;
}

export const config = {
  matcher: [
    {
      // API traffic is proxied to the backend, which sets its own headers.
      source: "/((?!api|_next/static|_next/image|favicon.ico).*)",
      missing: [
        { type: "header", key: "next-router-prefetch" },
        { type: "header", key: "purpose", value: "prefetch" },
      ],
    },
  ],
};
