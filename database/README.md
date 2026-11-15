# Database

PostgreSQL 16+ (developed against 17). Schema is owned **exclusively by Flyway migrations** that live
with the backend:

```
backend/src/main/resources/db/migration/V{n}__{snake_case_description}.sql
```

This folder holds database documentation and local-setup tooling only. It contains no schema SQL.
The full design (tables, constraints, indexes) is in [docs/DATABASE_DESIGN.md](../docs/DATABASE_DESIGN.md).

## Local databases

| Database | Purpose | Owner role |
|---|---|---|
| `trashbin_db` | development | `trashbin` |
| `trashbin_test_db` | automated tests (wiped and re-migrated freely) | `trashbin` |

Create them (idempotent). Passwords come from environment variables and are never stored in the repo:

```powershell
$env:PGPASSWORD           = '<postgres admin password>'
$env:TRASHBIN_DB_PASSWORD = '<password for the trashbin role>'
.\database\scripts\create-local-databases.ps1 -PgBin '<path to PostgreSQL bin>'
```

Then put the same role password in `backend/.env` (`TRASHBIN_DB_PASSWORD`, `TRASHBIN_TEST_DB_PASSWORD`).

The `citext` and `pg_trgm` extensions are *trusted* in PostgreSQL 13+, so the owner role can create them
through Flyway without superuser rights.

## Migration rules (summary)

1. Never edit a merged migration. Fix forward with a new one.
2. One concern per migration; DDL and data backfills separate.
3. Test on an empty database and on the previous schema version.
4. Hibernate runs with `ddl-auto=validate`; drift fails startup and tests.
5. Update `docs/DATABASE_DESIGN.md` in the same PR.

## Migration log

| Version | File | Milestone | Description |
|---|---|---|---|
| 1 | `V1__extensions.sql` | M0 | Enable `citext`, `pg_trgm` |
| 2 | `V2__users_and_refresh_tokens.sql` | M1 | Accounts and refresh-token sessions |
| 3 | `V3__archived_files_and_events.sql` | M2 | Archived files, extracted text, activity events |
| 4 | `V4__tags.sql` | M3a | Tags, file/tag links with composite FKs, favorite index |
| 5 | `V5__collections.sql` | M3b | Collections and membership with composite FKs |
| 6 | `V6__search.sql` | M3b | Search vector function, backfill, full-text and trigram indexes |
| 7 | `V7__lifecycle.sql` | M5 | Indexes for retention purge, "forgotten" and "on this day" |

Planned: see [DATABASE_DESIGN §8](../docs/DATABASE_DESIGN.md#8-migration-rules).

## Backup and restore (development)

```powershell
pg_dump -h localhost -U trashbin -Fc -f trashbin_db.dump trashbin_db
pg_restore -h localhost -U trashbin -d trashbin_db --clean --if-exists trashbin_db.dump
```

Back up the storage root (`TRASHBIN_STORAGE_ROOT`) at the same time; the database and the blobs form one
dataset. Dump files are git-ignored.
