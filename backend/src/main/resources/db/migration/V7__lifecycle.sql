-- Lifecycle and rediscovery (docs/DATABASE_DESIGN.md 3.3, M5).

-- Retention job: discarded files whose grace period has ended.
CREATE INDEX ix_archived_files_purge_after
    ON archived_files (purge_after) WHERE status = 'DISCARDED';

-- "Forgotten": files not opened for a long time.
CREATE INDEX ix_archived_files_owner_accessed
    ON archived_files (owner_id, status, last_accessed_at);

-- "On this day" by origin date.
CREATE INDEX ix_archived_files_owner_origin ON archived_files (owner_id, origin_date);
