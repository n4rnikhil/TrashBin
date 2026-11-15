# Project requirements

| | |
|---|---|
| **Status** | Approved baseline v1.0 (2026-11-15) |
| **Owner** | Product owner |
| **Related** | [SYSTEM_ARCHITECTURE](SYSTEM_ARCHITECTURE.md) · [API_CONTRACT](API_CONTRACT.md) · [DEVELOPMENT_ROADMAP](DEVELOPMENT_ROADMAP.md) |

## 1. Vision

Trash Bin is a hybrid **digital archive and digital memory**. Users upload files they no longer
actively need but may want again. The system preserves the original bytes untouched, together with the
context that makes a file findable years later: notes, tags, the reason it was kept, and its origin date.

> "Nothing is truly trash until you decide it is."

A file moves through `ARCHIVED → DISCARDED → PURGED`. *Discarding* is the user's explicit decision that
something is trash. Even then it remains recoverable for a retention window.

## 2. Users and scope

- **Primary user:** an individual archiving their own files. v1 has **single-user ownership**: every
  record belongs to exactly one user and is invisible to everyone else. There is no sharing and no team
  workspace, but the data model keeps an explicit `owner_id` so a workspace concept can be added later.
- **Administrator:** a role for operations only (actuator, storage reconciliation). There is no admin UI
  in v1 and an admin has no access to other users' file content through the API.
- **Platform:** web application only; local Windows development; no cloud hosting, paid
  infrastructure, or paid APIs.

### Glossary

| Term | Meaning |
|---|---|
| Archived file | A stored file with metadata, status `ARCHIVED`. |
| Discarded | Status `DISCARDED`: the user declared it trash. Recoverable until purge. |
| Purged | Row and blob physically deleted. Not recoverable. |
| Blob | The stored original bytes on the local filesystem. |
| Storage key | Server-generated UUID that locates a blob. Never derived from user input. |
| Context note | The user's "why I'm keeping this" text. |
| Origin date | Optional date the file relates to (not the upload date). |
| Access ticket | Short-lived signed URL token for downloading or previewing. |
| Retention | Days a discarded file remains recoverable before auto-purge. |

## 3. Functional requirements

Priority: **M** must, **S** should, **C** could. Each requirement lists its milestone
(see [DEVELOPMENT_ROADMAP](DEVELOPMENT_ROADMAP.md)).

### 3.1 Accounts and authentication

| ID | P | Requirement | MS |
|---|---|---|---|
| FR-A1 | M | Register, log in, log out, and log out of all devices. | M1 |
| FR-A2 | M | Sessions renew silently via refresh-token rotation with reuse detection. | M1 |
| FR-A3 | M | View/update profile; change password (revokes all sessions). | M1 |
| FR-A4 | S | List active sessions and revoke one. | M1 |
| FR-A5 | M | Registration can be disabled by configuration. | M1 |

### 3.2 Archiving

| ID | P | Requirement | MS |
|---|---|---|---|
| FR-F1 | M | Upload one or more files; the original bytes are stored unmodified. | M2 |
| FR-F2 | M | At upload, optionally set display name, notes, context note, origin date and tags. | M2 |
| FR-F3 | M | Detect duplicates per user by server-computed SHA-256 and let the user skip or keep both. Duplicates are detected against both `ARCHIVED` and `DISCARDED` files; the response states the existing file's status. | M2 |
| FR-F4 | M | Determine media type from file signatures, never from the client. Reject signature/extension mismatches for formats with strong signatures. | M2 |
| FR-F5 | S | Extract searchable text from PDF, Office and plain-text files asynchronously. | M4 |
| FR-F6 | S | Verify stored integrity on demand (recompute SHA-256, compare). | M5 |
| FR-F7 | M | Enforce per-file size limit and per-user quota (quota counts `ARCHIVED` and `DISCARDED`). | M2 |

### 3.3 Organizing and finding

| ID | P | Requirement | MS |
|---|---|---|---|
| FR-O1 | M | Tags: create, rename, merge, delete, assign; filter by tags (AND semantics). | M3 |
| FR-O2 | S | Collections: named, flat, many-to-many groupings of files. | M3 |
| FR-O3 | M | Full-text search over name, notes, context note, tag names and extracted text, ranked. | M3/M4 |
| FR-O4 | M | Filter by type group, date range, tag, collection, favorite, status; sort and paginate. | M3 |
| FR-O5 | S | Mark favorites. | M3 |
| FR-O6 | M | Edit metadata with optimistic concurrency (stale edits are rejected, not overwritten). | M3 |

### 3.4 Viewing and retrieving

| ID | P | Requirement | MS |
|---|---|---|---|
| FR-V1 | M | Detail view: all metadata and the activity timeline. | M2/M5 |
| FR-V2 | M | Secure download of the original file via short-lived access ticket. | M4 |
| FR-V3 | M | Inline preview for images, PDF and text. Every other type is download-only. | M4 |
| FR-V4 | C | Image thumbnails. | post-v1 |

### 3.5 Lifecycle

| ID | P | Requirement | MS |
|---|---|---|---|
| FR-L1 | M | Discard a file (moves to Discarded view, still recoverable). | M5 |
| FR-L2 | M | Restore a discarded file to the archive. | M5 |
| FR-L3 | M | Permanently purge a discarded file (confirmation required). | M5 |
| FR-L4 | M | Auto-purge discarded files after the retention period (default 30 days; `0` disables). The UI shows the remaining time. | M5 |

### 3.6 Digital memory (no AI)

| ID | P | Requirement | MS |
|---|---|---|---|
| FR-M1 | S | "On this day": files archived or originating on today's month/day in earlier years. | M5 |
| FR-M2 | S | "Forgotten": files not accessed for a long time. | M5 |
| FR-M3 | S | "Surprise me": a random archived file. | M5 |
| FR-M4 | S | Per-file activity timeline (archived, edited, downloaded, previewed, discarded, restored). | M5 |
| FR-M5 | S | Dashboard statistics: counts, storage used, quota. | M5 |

## 4. Non-functional requirements

| ID | Area | Requirement |
|---|---|---|
| NFR-P1 | Performance | With 10,000 files for one user, list and search p95 < 300 ms; metadata save p95 < 200 ms (measured locally in M3). |
| NFR-P2 | Memory | Upload and download stream with bounded memory; heap use does not grow with file size. |
| NFR-S1 | Security | OWASP Top 10 mitigations and ASVS L2 as the review reference. See [SECURITY_GUIDELINES](SECURITY_GUIDELINES.md). |
| NFR-S2 | Security | No secret in the repository, logs or error responses; the app refuses to start with missing or weak secrets. |
| NFR-R1 | Reliability | No DB/filesystem failure may leave a visible file without a blob, or a blob that can never be cleaned. A reconciler reports discrepancies. |
| NFR-D1 | Durability | SHA-256 stored per file; writes are atomic; a documented backup procedure covers DB and storage together. |
| NFR-O1 | Observability | Structured logs with a trace id; Actuator health and metrics; security events logged. |
| NFR-A1 | Accessibility | WCAG 2.1 AA; fully keyboard-operable; responsive from 360 px width. |
| NFR-C1 | Compatibility | Latest Chrome, Edge and Firefox. |
| NFR-M1 | Maintainability | Module boundaries enforced by ArchUnit; strict TypeScript; coverage gates in [CODING_STANDARDS](CODING_STANDARDS.md#testing-expectations). |
| NFR-X1 | Configuration | All environment-specific values externalized; no hardcoded credentials. |

## 5. Default limits (all configurable)

| Limit | Default | Config key |
|---|---|---|
| Max file size | 250 MB | `TRASHBIN_MAX_FILE_SIZE_MB` |
| Per-user quota | 5 GB | `TRASHBIN_USER_QUOTA_GB` |
| Discard retention | 30 days | `TRASHBIN_RETENTION_DAYS` |
| Access ticket TTL | 60 s (download), 5 min (preview) | `TRASHBIN_DOWNLOAD_TICKET_TTL`, `TRASHBIN_PREVIEW_TICKET_TTL` |
| Page size | default 25, max 100 | fixed |
| Notes length | 10,000 chars | fixed |
| Context note length | 2,000 chars | fixed |
| Tags per file | 20 | fixed |
| Password length | 12 chars minimum, 72 bytes (UTF-8) maximum | fixed (BCrypt limit) |
| Extracted text stored | 1,000,000 chars | `TRASHBIN_EXTRACTION_MAX_CHARS` |

## 6. Constraints

- Fixed stack: Next.js, React, strict TypeScript, Tailwind, shadcn/ui, Lucide; Java 21, Spring Boot 3.x,
  PostgreSQL, Flyway, Apache Tika. See [ADR-001](DECISIONS/ADR-001-architecture.md).
- Local Windows development; no cloud hosting; no paid infrastructure or APIs.
- No unnecessary microservices, Docker requirements, or dependencies.
- No fake or non-functional features; no AI in v1.

## 7. Assumptions

- A single machine runs the database, backend, frontend and storage directory.
- Whole-disk encryption (BitLocker) is the at-rest protection; application-level encryption is out of
  scope but the `BlobStore` port allows adding it.
- Users have no email service available; hence no email verification or self-service password reset
  in v1 (see open items in [ADR-001](DECISIONS/ADR-001-architecture.md#6-open-items)).

## 8. Out of scope for v1

Sharing and collaboration · AI, OCR, embeddings · email notifications and verification · file
versioning · bulk ZIP export · mobile apps · malware scanning (an extension point exists) · thumbnails
for non-image types · internationalization · admin UI.

## 9. Acceptance summary

v1 is accepted when every **M** requirement passes its milestone's definition of done in
[DEVELOPMENT_ROADMAP](DEVELOPMENT_ROADMAP.md), the end-to-end Playwright journey (register → upload →
search → preview → download → discard → restore → purge) passes, and the security checklist in
[SECURITY_GUIDELINES](SECURITY_GUIDELINES.md#12-release-security-checklist) is complete.
