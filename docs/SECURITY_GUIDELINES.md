# Security guidelines

| | |
|---|---|
| **Status** | Approved baseline v1.0 (2026-11-15) |
| **Audience** | Everyone who writes or reviews code. These rules are binding. |
| **Reference** | OWASP Top 10, OWASP ASVS L2 |
| **Related** | [API_CONTRACT](API_CONTRACT.md) · [DATABASE_DESIGN](DATABASE_DESIGN.md) · [SYSTEM_ARCHITECTURE](SYSTEM_ARCHITECTURE.md) |

## 1. Principles

1. **Deny by default.** Every route requires authentication unless explicitly allow-listed.
2. **Ownership is checked on every access**, in the service layer, using the authenticated principal.
3. **A UUID is not authorization.** Knowing an ID grants nothing.
4. **Server decides.** Media type, size, hash, owner, status and storage paths are computed or enforced
   server-side; client values are hints at most.
5. **Fail closed, fail quiet.** Errors reveal nothing about internals or other users' data.
6. **Defense in depth.** Application checks *and* database constraints (composite FKs) *and* tests.
7. **No secrets in code, config files in git, logs, or responses.**

## 2. Threat model summary

| Threat | OWASP | Primary mitigations |
|---|---|---|
| Accessing another user's file/metadata by guessing an ID (BOLA/IDOR) | A01 | Owner-scoped queries; 404 on foreign resources; composite FKs; cross-tenant tests on every endpoint |
| Credential stuffing, brute force | A07 | BCrypt; lockout; per-IP throttle; uniform error responses |
| Token theft / replay | A02, A07 | In-memory access token; HttpOnly refresh cookie; short TTLs; rotation with reuse detection |
| Stored/reflected XSS via names, notes or previewed content | A03 | React escaping; no `dangerouslySetInnerHTML`; SVG/HTML never inline; strict CSP; `nosniff` |
| Malicious or oversized upload | A04, A08 | Stream-enforced size caps; signature check; quota; denylist; scanner hook; never executed |
| Path traversal / arbitrary file read-write | A01 | UUID-only disk names; normalize + prefix check; symlink rejection |
| Parser exploitation / decompression bombs (Tika) | A06 | Type allowlist; input/output/time caps; bounded executor; failure isolated per file |
| CSRF | A01 | Bearer-header API; cookie endpoints guarded by SameSite=Strict + Origin check + custom header |
| Injection (SQL, tsquery, header) | A03 | Bound parameters; `websearch_to_tsquery`; sort whitelist; sanitized `Content-Disposition` |
| Mass assignment | A04 | Request-specific DTO records; entities never bound from input |
| Information leakage | A05 | Problem+json hygiene; no stack traces; uniform auth errors |
| Misconfiguration | A05 | Actuator/Swagger locked per profile; startup validation of secrets; security headers |
| Vulnerable dependencies | A06 | Minimal dependency list; scheduled `npm audit` and Maven dependency-check |
| Insufficient logging | A09 | Structured logs, trace id, security events recorded, no sensitive data |
| Resource exhaustion | A04 | Page-size caps, quotas, bounded executors, rate limits, statement timeout |
| Silent data corruption/loss | – | SHA-256 stored; verify endpoint; atomic writes; reconciler; soft-delete window; backups |

## 3. Authentication

### 3.1 Passwords

- **BCrypt, cost 12**, via `DelegatingPasswordEncoder` (`{bcrypt}` prefix) so the algorithm can be
  upgraded later without a flag day.
- **Length policy: minimum 12 characters; maximum 72 bytes in UTF-8.** BCrypt only considers the first
  72 bytes, so longer input is **rejected with a validation error** rather than silently truncated. The
  limit is measured in *bytes*, not characters. No composition rules.
- Never log passwords, hashes or password-related request bodies.

### 3.2 Access token (JWT)

- HS256, signed with `TRASHBIN_JWT_SECRET` (≥ 32 decoded bytes, validated at startup).
- Lifetime **10 minutes**. Claims: `sub` (user UUID), `roles`, `jti`, `iss`, `aud`, `iat`, `exp`.
- Validated by Spring Security's resource-server support; no hand-written JWT parsing.
- Held **in memory only** in the browser. Never `localStorage`, `sessionStorage`, URLs or logs.
- After a password change the tokens already issued remain valid until `exp` (≤ 10 min). This window is
  accepted; refresh tokens are revoked immediately.

### 3.3 Refresh token

- Opaque, 256-bit random, delivered **only** as a cookie:
  `tb_refresh; HttpOnly; SameSite=Strict; Path=/api/v1/auth; Secure` (`Secure` governed by
  `TRASHBIN_COOKIE_SECURE`; always on outside local `dev`).
- The database stores only the **SHA-256 hash** of the token.
- **Rotation on every use.** Each refresh marks the presented token `used_at`, issues a successor in the
  same family, and returns a new cookie.
- **Reuse detection.** Presenting an already-used token revokes the entire family → `401
  INVALID_REFRESH_TOKEN`. Exception: a **20-second grace window** after rotation tolerates multi-tab
  races; within it the replay is rejected *without* revoking the family.
- Lifetimes: sliding **14 days** per token, absolute **30 days** per family.
- Logout revokes the family; logout-all and password change revoke every family of the user.
- The client coordinates refresh with a single-flight lock and the Web Locks API across tabs.

### 3.4 Login hardening

- Failed attempts increment `users.failed_login_count`; five consecutive failures lock the account for
  15 minutes. A successful login resets the counter.
- A locked account returns the **same** `401 INVALID_CREDENTIALS` as a wrong password.
- Unknown emails perform a dummy BCrypt comparison to equalize timing.
- Per-IP throttling (default 10 requests/minute on `/auth/*`) via a small in-memory token bucket.

### 3.5 Client IP

The backend sits behind the Next.js proxy, so the TCP peer is always loopback. Per-IP throttling and
the `refresh_tokens.ip_address` column are meaningful only if the backend honors `X-Forwarded-For`
**from trusted proxies only** (`TRASHBIN_TRUSTED_PROXIES`, default loopback). Use Spring's forwarded
header handling with that allowlist; never trust the header from arbitrary peers. Verified in M1.

### 3.6 CSRF and CORS

- Bearer-header endpoints are not CSRF-prone: browsers do not attach the header cross-site.
- The only cookie-authenticated endpoints are `/auth/refresh` and `/auth/logout`. They require **all of**:
  `SameSite=Strict` cookie, `Origin` equal to `TRASHBIN_ALLOWED_ORIGIN`, and header
  `X-Requested-With: trashbin`.
- CORS: because the browser uses the proxy, no cross-origin calls are expected. Spring still configures
  an **exact-origin allowlist** (`TRASHBIN_ALLOWED_ORIGIN`); no wildcard; credentials allowed only for
  that origin.

## 4. Authorization

Three layers, all mandatory:

1. **Authentication** on every route except an explicit allow-list: `register`, `login`, `refresh`,
   `/actuator/health`, `/content/{ticket}` (ticket-authenticated), and dev-only API docs.
2. **Role** checks via `@PreAuthorize("hasRole('ADMIN')")` on admin endpoints (actuator, reconcile).
   `ADMIN` confers **no** access to other users' files through the API.
3. **Ownership.**
   - Owned entities are read and written only through owner-scoped repository methods
     (`findByIdAndOwnerId`, `findAllByOwnerId…`). Plain `findById` on owned entities is **banned**
     (ArchUnit rule).
   - The owner id comes from the security context, **never** from request bodies, parameters or paths.
   - Join operations (tags, collections) verify that every referenced id is owned by the caller;
     composite foreign keys back this up in the database.
   - Foreign or missing resources both return **404**.

Every new endpoint ships with a cross-tenant integration test: user B attempts every operation on user
A's resources and receives 404 (or 403 for admin-only routes).

## 5. Upload security

Ordered pipeline (details: [SYSTEM_ARCHITECTURE §4.2](SYSTEM_ARCHITECTURE.md#42-upload)):

1. Authenticate; check quota and the declared size early.
2. Stream to `staging/`, counting bytes against a **hard cap** (headers are not trusted) and computing SHA-256.
3. Detect the type from **file signatures** (Tika). Compare with the extension and declared type; for
   formats with strong signatures (PDF, PNG, JPEG, GIF, WebP, ZIP/OOXML) a contradiction is rejected
   (`FILE_SIGNATURE_MISMATCH`). Text-like and unknown formats are allowed.
4. Apply the extension denylist (`TRASHBIN_BLOCKED_EXTENSIONS`, empty by default) and the `UploadScanner`
   hook (no-op in v1; the integration point for a future malware scanner).
5. Duplicate check (owner, SHA-256).
6. Atomic move to `blobs/`; DB commit; compensating delete on failure.

Rules:

- Uploaded files are **stored and served, never executed, unpacked, or rendered** by the server (other than
  bounded Tika text extraction).
- The original filename is metadata. It is length-limited, stripped of control characters and path
  separators on display/headers, and **never** used to build a path.
- Archive formats (zip etc.) are stored as opaque blobs; they are not expanded.

## 6. File storage security

- Disk name = server-generated UUID `storage_key` only; extension-less.
- Path resolution: `root.resolve(shard/key).normalize()` must start with `root`; reject symlinks
  (`NOFOLLOW_LINKS`); keys parsed as UUIDs before use.
- Storage root is outside the repository, writable only by the service account (restrict Windows ACLs).
- `staging/` is swept regularly. Orphan blobs are removed by the reconciler.
- At-rest encryption is disk-level (BitLocker) in v1; application-level encryption can be added behind
  `BlobStore` later.

## 7. Content serving and XSS

- Download and preview happen through **access tickets** (HMAC-SHA-256, key separate from the JWT key).
  Lifetimes: **download 60 s, preview 5 min**. Tickets are bound to user, file and purpose and are
  re-checked against the database on use.
- Ticket URLs may appear in browser history/referrers: mitigations are short TTLs,
  `Referrer-Policy: no-referrer`, and redaction of `/content/*` paths in access logs.
- Previewable allowlist: `image/png`, `image/jpeg`, `image/gif`, `image/webp`, `text/plain`, and
  `application/pdf` once its strategy is decided (see below). **SVG and HTML are never inline.** Everything else is `attachment` with
  `application/octet-stream`.
- Headers on every content response: `X-Content-Type-Options: nosniff`, `Cache-Control: private,
  no-store`, `Content-Disposition` with an RFC 6266 `filename*` value whose input has CR/LF/control
  characters removed.
- Content responses carry a restrictive CSP (`default-src 'none'; sandbox`) and are embeddable only
  by this origin (`frame-ancestors 'self'`). The **PDF exception**: browser-native PDF viewers do not
  work inside a CSP `sandbox`. The PDF preview strategy is an open item decided in M4
  ([ADR-001 §6](DECISIONS/ADR-001-architecture.md#6-open-items)); until then PDFs are download-only.
- Application pages: strict CSP (nonce-based scripts, `frame-ancestors 'none'`, `object-src 'none'`,
  `base-uri 'self'`), `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff`,
  `Permissions-Policy` minimal, HSTS when served over HTTPS. Development may relax script rules for
  hot reload; production may not. Any middleware that sets CSP excludes `/api` routes.
- React escaping is relied upon; `dangerouslySetInnerHTML` is forbidden. User text (names, notes, tags)
  is rendered as text only.

## 8. Input validation

- Bean Validation on every request record; **additionally** service-level invariants (state transitions,
  ownership, limits). Frontend validation is UX only.
- Whitelist sort fields; cap page size; bound string lengths (see
  [PROJECT_REQUIREMENTS §5](PROJECT_REQUIREMENTS.md#5-default-limits-all-configurable)).
- All SQL via JPA/Spring Data or bound parameters. No string-concatenated SQL or JPQL.
- Search text is only ever a bound parameter to `websearch_to_tsquery`.
- Reject unexpected content types on JSON endpoints.

## 9. Secrets and configuration

- Secrets come from environment variables or git-ignored `.env` files. Only `*.env.example` files with
  placeholders are committed.
- At startup the app **fails** if `TRASHBIN_JWT_SECRET` or `TRASHBIN_TICKET_SECRET` is missing, shorter
  than 32 decoded bytes, equal to each other, or a known placeholder.
- `NEXT_PUBLIC_*` variables must never carry secrets (none are used in v1).
- If a secret is committed or leaked: rotate it first, then remove it from history. Rotating
  `TRASHBIN_JWT_SECRET` invalidates all access tokens; rotating `TRASHBIN_TICKET_SECRET` invalidates all tickets.
- Rate-limit and lockout state must not be derivable by unauthenticated clients beyond what the
  response codes already reveal.

## 10. Logging and monitoring

| Never log | Always log (with `traceId`, `userId` if known) |
|---|---|
| passwords, tokens, cookies, `Authorization` headers, tickets | login success/failure, lockout |
| file contents, **filenames**, notes | refresh reuse detection and family revocation |
| full request bodies | password change, logout-all |
| stack traces in HTTP responses | upload rejections by reason code, purge, reconcile findings |

Log file **ids**, not names. Use parameterized logging; never concatenate user input into log messages
(log injection). `/actuator/health` exposes status only; other actuator endpoints are admin-only and off by default.

## 11. Accepted residual risks

| Risk | Rationale |
|---|---|
| Registration reveals whether an email is registered (`409 EMAIL_ALREADY_REGISTERED`) | Without email verification the conflict cannot be hidden. Mitigated by throttling and the ability to disable registration. |
| Access tokens remain valid ≤ 10 min after logout/password change | Stateless validation by design; short TTL; refresh tokens revoked immediately. |
| Duplicate detection reveals a user's *own* file existence | Intended behavior; scoped to the owner. |
| No malware scanning in v1 | Files are never executed or rendered by the server; downloads are `attachment`; scanner hook provided. |
| Tika runs in-process | Mitigated by allowlist, caps and timeouts; out-of-process parsing is the documented escalation. |

## 12. Release security checklist

Complete before declaring v1 (milestone M6) and re-run for each release:

- [ ] Cross-tenant test suite passes for **every** endpoint
- [ ] ArchUnit rules pass (no plain `findById` on owned entities; no entities in controllers)
- [ ] Startup refuses weak/missing/identical secrets (tested)
- [ ] Refresh rotation, reuse detection, grace window and logout-all tested
- [ ] Upload tests: oversize stream, signature mismatch, traversal-style filenames, duplicate, quota, DB-failure compensation
- [ ] Content headers verified: nosniff, no-store, CSP, `Content-Disposition` injection attempts
- [ ] No `localStorage`/`sessionStorage` token usage (grep + review)
- [ ] `npm audit` and Maven dependency-check reviewed; no unresolved high/critical findings
- [ ] Actuator and Swagger unreachable in `prod` profile (tested)
- [ ] Logs reviewed: no tokens, passwords, filenames, or file contents
- [ ] Security headers verified on app pages and content responses
- [ ] Backup and restore procedure executed once successfully
- [ ] Every item in §11 still accurate

## 13. Reviewer quick checks

For any PR touching these areas, reviewers ask: *Who is the owner and where is it checked? What happens
for another user's id? What does the error reveal? What gets logged? Could a filename, notes field or
header value reach a path, a header or the DOM unescaped? What bound limits the input?*
