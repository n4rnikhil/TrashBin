# Changelog

## v1.0.0 — unreleased

First release of Trash Bin — Stores Your Trash. A private archive for files you are not ready to lose.

**Archive**
- Accounts with silent session renewal, sign-out everywhere, password change; registration can be turned off.
- Upload many files at once; originals stored byte for byte; type detected from file contents; duplicates
  detected with "keep both" or "skip"; per-file size limit and per-user quota.
- Display name, notes, "why you kept it", origin date, favorites, tags and collections; edits never
  overwrite each other silently.

**Find and open**
- Ranked search over names, notes, tags and the text inside PDF, Office and text files; filters by type,
  date, tag, collection, favorite and status.
- Secure downloads and inline previews for images, PDF and text; everything else downloads only.

**Lifecycle and memory**
- Discard → restore, or delete permanently; discarded files are deleted automatically after 30 days
  (configurable, can be turned off).
- Integrity check per file, activity timeline, Rediscover ("on this day", forgotten files, surprise me),
  dashboard counts and storage meter.

**Operations and security**
- Hardened `prod` profile (no API docs, JSON logs, Secure cookie, HSTS on HTTPS), nonce-based page CSP.
- Backup, restore and whole-archive integrity scripts; operator password recovery.
- Accessibility: WCAG 2.1 AA checks on all core screens, keyboard-only use, works at 360 px.

**Known before tagging:** Spring Framework 6.2 / Spring Security 6.5 advisories pending the Spring Boot
version decision; license not yet chosen. See [docs/VERIFICATION.md](docs/VERIFICATION.md).
