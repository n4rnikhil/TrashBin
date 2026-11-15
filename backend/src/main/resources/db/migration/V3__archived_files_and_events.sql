-- Archived files, extracted text and the activity log (docs/DATABASE_DESIGN.md 3.3, 3.4, 3.8).
-- Composite (id, owner_id) keys let child tables prove at the database level that they reference
-- a file of the same owner.

CREATE TABLE archived_files (
    id                  uuid         PRIMARY KEY,
    owner_id            uuid         NOT NULL REFERENCES users (id),
    original_filename   varchar(255) NOT NULL,
    display_name        varchar(255) NOT NULL,
    media_type          varchar(127) NOT NULL,
    declared_media_type varchar(255),
    type_group          varchar(20)  NOT NULL,
    size_bytes          bigint       NOT NULL,
    sha256              varchar(64)  NOT NULL,
    storage_key         uuid         NOT NULL,
    status              varchar(20)  NOT NULL,
    extraction_status   varchar(20)  NOT NULL,
    notes               text,
    context_note        text,
    origin_date         date,
    favorite            boolean      NOT NULL DEFAULT false,
    search_vector       tsvector     NOT NULL DEFAULT ''::tsvector,
    archived_at         timestamptz  NOT NULL,
    last_accessed_at    timestamptz,
    discarded_at        timestamptz,
    purge_after         timestamptz,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_archived_files_id_owner UNIQUE (id, owner_id),
    CONSTRAINT uq_archived_files_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_archived_files_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_archived_files_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_archived_files_status CHECK (status IN ('ARCHIVED', 'DISCARDED')),
    CONSTRAINT ck_archived_files_extraction CHECK
        (extraction_status IN ('PENDING', 'DONE', 'SKIPPED', 'FAILED')),
    CONSTRAINT ck_archived_files_type_group CHECK (type_group IN
        ('IMAGE', 'DOCUMENT', 'SPREADSHEET', 'PRESENTATION', 'TEXT', 'AUDIO', 'VIDEO', 'ARCHIVE', 'OTHER')),
    CONSTRAINT ck_archived_files_notes CHECK (char_length(notes) <= 10000),
    CONSTRAINT ck_archived_files_context CHECK (char_length(context_note) <= 2000),
    CONSTRAINT ck_archived_files_discarded CHECK ((status = 'DISCARDED') = (discarded_at IS NOT NULL))
);

CREATE INDEX ix_archived_files_owner_status_archived
    ON archived_files (owner_id, status, archived_at DESC);
-- Not unique: "keep both" duplicates are allowed.
CREATE INDEX ix_archived_files_owner_sha256 ON archived_files (owner_id, sha256);
CREATE INDEX ix_archived_files_owner_type ON archived_files (owner_id, type_group, status);

CREATE TABLE file_text_content (
    file_id      uuid        PRIMARY KEY REFERENCES archived_files (id) ON DELETE CASCADE,
    content      text        NOT NULL,
    content_tsv  tsvector    GENERATED ALWAYS AS (to_tsvector('english'::regconfig, content)) STORED,
    truncated    boolean     NOT NULL,
    extracted_at timestamptz NOT NULL
);

CREATE INDEX ix_file_text_content_tsv ON file_text_content USING gin (content_tsv);

CREATE TABLE file_events (
    id          uuid        PRIMARY KEY,
    file_id     uuid        NOT NULL,
    owner_id    uuid        NOT NULL,
    event_type  varchar(30) NOT NULL,
    details     jsonb       NOT NULL DEFAULT '{}'::jsonb,
    occurred_at timestamptz NOT NULL,
    CONSTRAINT fk_file_events_file FOREIGN KEY (file_id, owner_id)
        REFERENCES archived_files (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT ck_file_events_type CHECK (event_type IN
        ('ARCHIVED', 'METADATA_EDITED', 'DOWNLOADED', 'PREVIEWED', 'DISCARDED', 'RESTORED',
         'INTEGRITY_VERIFIED'))
);

CREATE INDEX ix_file_events_file_time ON file_events (file_id, occurred_at DESC);
