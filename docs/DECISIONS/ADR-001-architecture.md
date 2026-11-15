# ADR-001: Overall architecture

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-11-15 |
| **Deciders** | Product owner (approval), principal architect (proposal) |
| **Supersedes** | – |
| **Related** | [SYSTEM_ARCHITECTURE](../SYSTEM_ARCHITECTURE.md) · [SECURITY_GUIDELINES](../SECURITY_GUIDELINES.md) · [DATABASE_DESIGN](../DATABASE_DESIGN.md) |

## 1. Context

Trash Bin is a web-only archive/memory application for individual users, developed and run locally on
Windows with no cloud hosting and no paid services. The stack is fixed by the brief: Next.js, React,
strict TypeScript, Tailwind, shadcn/ui; Java 21, Spring Boot 3.x; PostgreSQL with Flyway; Apache Tika; local
filesystem storage. The application stores user files that may be sensitive, so security and data
durability dominate the design. Scale is small (a handful of users, ≤ 100k files), which favors
simplicity over distribution.

Forces: limited operational capacity; a requirement for strong ownership isolation; large binary
uploads; need for full-text search without extra infrastructure; desire to keep frontend and backend
responsibilities cleanly separated.

## 2. Decisions

Each decision lists the alternatives rejected.

### AD-1 Modular monolith backend
One Spring Boot application packaged **by feature** (`auth`, `user`, `archive`, `tag`, `collection`,
`rediscover`, `activity`, `storage`, `content`, `stats`) with layered internals (`web → service →
repository → domain`). Module boundaries are enforced by ArchUnit tests.
*Rejected:* microservices (operational cost with no benefit at this scale); package-by-layer (weak
boundaries); Spring Modulith (extra dependency; ArchUnit suffices).

### AD-2 Separate frontend and backend, joined by a same-origin proxy
Next.js serves the UI and rewrites `/api/*` to the Spring backend. The browser sees one origin.
*Why:* first-party refresh cookie, no reliance on CORS, backend remains UI-agnostic and independently testable.
*Rejected:* direct cross-origin calls (cookie/CORS complexity); Next.js Route Handlers re-implementing API logic (blurs responsibilities).

### AD-3 PostgreSQL for metadata and search; filesystem for bytes
Metadata, auth state and full-text search live in PostgreSQL (`tsvector`, GIN, `pg_trgm`). File bytes live on
local disk behind a `BlobStore` port. UUID primary keys; Flyway migrations; Hibernate `validate`.
*Rejected:* storing blobs in the database (streaming and backup cost); Elasticsearch/OpenSearch (needless infrastructure); H2 for tests (dialect drift, no FTS).

### AD-4 Authentication model
Short-lived **JWT access token** (10 min, HS256) held in browser memory; **opaque rotating refresh token**
(HttpOnly, SameSite=Strict cookie scoped to `/api/v1/auth`, stored hashed, family-based reuse detection).
BCrypt (cost 12, `DelegatingPasswordEncoder`) with a 72-byte password maximum. Account lockout and per-IP throttling.
*Why:* limits XSS blast radius (no persistent script-readable token); stateless request authentication;
revocable sessions.
*Rejected:* tokens in `localStorage` (XSS-exfiltratable); server sessions only (less aligned with the brief);
a Next.js BFF holding tokens (stronger XSS posture, but couples Next to auth/CSRF and forces server-side token
handling; revisit if SSR of authenticated pages becomes necessary); Argon2id (needs BouncyCastle; upgrade path preserved by the delegating encoder); RS256 (no second verifier exists).

### AD-5 Ownership as the authorization core
Single-user ownership with `owner_id` on every owned row; owner-scoped repository methods only; foreign
resources return 404; join tables use composite foreign keys; a cross-tenant test accompanies every endpoint.
A `workspace` concept is deferred but not precluded.
*Rejected:* relying on unguessable UUIDs; role-only authorization; Hibernate filters/row-level security as the sole mechanism (hidden magic; may be added as defense in depth later).

### AD-6 File lifecycle: `ARCHIVED → DISCARDED → PURGED`
"Discard" is the explicit user decision that a file is trash; discarded files remain restorable for a
configurable retention (default 30 days) before automatic purge. Purge removes the row and blob; the row's
absence represents `PURGED`.
*Rejected:* immediate hard delete (contradicts the product idea); keeping a tombstone row for purged files (privacy and complexity).

### AD-7 Local storage and upload protocol
Blobs are named only by a server-generated UUID `storage_key`, sharded under a configurable root; uploads stream
to `staging/` (hash and size cap in one pass), are type-verified from signatures, then atomically moved into
place **before** the DB commit, with compensating deletion on failure and a reconciler for stragglers. Purge
deletes the blob `afterCommit`. No cross-user physical deduplication.
*Why:* DB and filesystem are not transactional together; this ordering plus reconciliation gives "no visible
file without a blob" and eventual cleanup of everything else.

### AD-8 Content delivery through signed access tickets
Downloads and previews use HMAC-signed, purpose-bound, short-lived tickets (download 60 s, preview 5 min)
issued by an authenticated endpoint and re-validated against the database on use. Non-previewable types, SVG
and HTML are always served as `attachment`.
*Why:* plain `<a href>`/`<img src>` cannot send an `Authorization` header; blob-fetching in JS defeats native
streaming and `Range`.
*Rejected:* long-lived signed URLs; cookies for content auth; fetching blobs into memory.

### AD-9 Text extraction
Apache Tika for type detection and text extraction (selected parser modules only), asynchronously on a
bounded executor with type allowlist, input/output/time caps, and per-file failure isolation.
*Rejected for v1:* out-of-process parsing (kept as an escalation path); OCR and AI features.

### AD-10 Error handling and observability
RFC 9457 problem+json with stable `code` and `traceId`; a typed exception hierarchy mapped by one
`@RestControllerAdvice` with a single last-resort handler. Structured JSON logs in `prod`; Actuator health
public (status only), everything else admin-only.

### AD-11 Tooling and dependency posture
Maven (with wrapper); Spotless + `-Xlint:all -Werror`; no Lombok, no MapStruct (explicit mappers); npm; TanStack
Query, react-hook-form + zod, openapi-typescript (dev), Vitest/Testing Library/MSW, Playwright. springdoc for
OpenAPI with a committed snapshot used to generate frontend types. Any further dependency needs a written justification.

### AD-12 Test database strategy
Integration tests run against a real PostgreSQL: a dedicated local `trashbin_test_db` database by default
(satisfies "no unnecessary Docker"); Testcontainers is an optional profile.

## 3. Consequences

**Positive**
- Simple local operation: one backend process, one frontend process, one database, one directory.
- Strong, layered isolation of user data (code, tests, DB constraints).
- No script-readable long-lived credentials in the browser.
- The storage and search implementations are replaceable behind ports without touching business rules.

**Negative / costs**
- The authenticated UI is client-rendered; server components cannot call authenticated APIs (accepted).
- Cookie-authenticated refresh endpoints need extra CSRF controls (SameSite, Origin, custom header).
- DB/filesystem non-atomicity requires the reconciler and careful operation ordering.
- Per-IP controls depend on correct trusted-proxy handling behind the Next.js proxy.
- No self-service password recovery in v1.
- In-process Tika is a (bounded) attack surface.
- Running tests requires a local PostgreSQL installation.

## 4. Approved decisions carried from planning

| ID | Decision | Where it lives |
|---|---|---|
| D1 | 30-day discard retention, configurable, `0` disables auto-purge | PROJECT_REQUIREMENTS §5 |
| D2 | Single-user ownership in v1; workspace deferred | AD-5 |
| D3 | No email flows (verification, reset) in v1 | PROJECT_REQUIREMENTS §7–8 |
| D4 | Accept any file type with signature checks; non-previewable types attachment-only; denylist empty by default; scanner hook only | SECURITY_GUIDELINES §5, §7 |
| D5 | Spring Boot 3.x — version support to be verified in M0 | §6 Open items |
| D6 | Native PostgreSQL for dev/tests; Testcontainers optional | AD-12 |
| D7 | BCrypt cost 12 | AD-4 |
| D8 | Maven | AD-11 |
| D9 | No Lombok / MapStruct | AD-11 |

## 5. Refinements made while documenting

These adjust the approved plan and are reflected consistently in all documents:

1. **Password maximum is 72 bytes (UTF-8), not 128 characters** — BCrypt ignores input beyond 72 bytes.
2. **Preview tickets live 5 minutes; download tickets 60 seconds** — viewers issue further range requests
   after the first response, so a 60-second preview ticket would break them.
3. **Content responses use `frame-ancestors 'self'`**; application pages use `'none'`. In-app previews are
   framed by the same origin.
4. **`type_group`** is stored on `archived_files` (derived at upload) so type filters are indexable.
5. **`refresh_tokens.family_expires_at`** is added to implement the absolute 30-day cap, and `collections` gets
   `name_normalized` for uniqueness, mirroring tags.
6. **Duplicate detection** considers both `ARCHIVED` and `DISCARDED` files; quota counts both.
7. **Lockout is indistinguishable from a wrong password** (`401 INVALID_CREDENTIALS`) to avoid revealing account state.
8. **Registration enumeration** is an accepted, documented residual risk (SECURITY_GUIDELINES §11).
9. **Trusted-proxy handling** for `X-Forwarded-For` is required for per-IP controls to work behind the Next proxy.
10. **Upload through the Next proxy** must be spiked in M0 (size and timeout behavior).

## 6. Open items

| # | Item | Needed by | Proposed resolution |
|---|---|---|---|
| 1 | **Spring Boot version.** ~~Confirm 3.x support.~~ **Resolved 2026-11-15:** pinned to **3.5.16**, the newest 3.x published on Maven Central (OSS patch releases are still flowing). The official end-of-support date was not independently confirmed; re-check before v1.0 and plan a move to 4.x if the 3.5 line stops receiving fixes. | M0 | Done. |
| 2 | **Admin bootstrap and password recovery.** **Bootstrap resolved in M1:** the account registering with `TRASHBIN_BOOTSTRAP_ADMIN_EMAIL` becomes `ADMIN`. **Still open:** recovery for a user who forgot their password (no email in v1). | recovery: before v1.0 | Originally recommended: `TRASHBIN_BOOTSTRAP_ADMIN_EMAIL` promotes the matching registrant; recovery via an admin CLI/profile command that sets a new password and revokes sessions. |
| 3 | **PDF preview strategy (ADR-004).** Browser-native PDF viewers do not render under a CSP `sandbox`. | M4 | Spike native viewing with an adjusted CSP for PDFs versus bundling a viewer (new dependency); PDFs are download-only until decided. |
| 4 | **Upload via Next proxy.** **Resolved in M2:** the rewrite proxy truncates bodies over 10 MB, so `/api/v1/files` uses a streaming route handler instead; single origin kept (AD-2 unchanged). | Done | — |
| 5 | **Line-ending policy file.** **Resolved:** `.gitattributes` added in the M0 review. | Done | — |
| 6 | **Project license.** | before publishing | Product owner to choose. |

## 7. Review

Revisit this ADR when: server-side rendering of authenticated pages is required, a second user-facing client
appears, multi-user sharing is planned, deployment moves off a single machine, or any open item's resolution
changes an AD above. Changes are made by adding a new ADR that supersedes the affected decision.
