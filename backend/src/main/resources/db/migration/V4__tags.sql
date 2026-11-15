-- Tags and their assignment to files (docs/DATABASE_DESIGN.md 3.5, 3.7).
-- file_tags carries owner_id with composite foreign keys, so the database itself refuses to link a
-- file to a tag of another user.

CREATE TABLE tags (
    id              uuid        PRIMARY KEY,
    owner_id        uuid        NOT NULL REFERENCES users (id),
    name            varchar(50) NOT NULL,
    name_normalized varchar(50) NOT NULL,
    color           varchar(7),
    created_at      timestamptz NOT NULL,
    CONSTRAINT uq_tags_owner_name UNIQUE (owner_id, name_normalized),
    CONSTRAINT uq_tags_id_owner UNIQUE (id, owner_id),
    CONSTRAINT ck_tags_name CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT ck_tags_color CHECK (color ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE TABLE file_tags (
    file_id    uuid        NOT NULL,
    tag_id     uuid        NOT NULL,
    owner_id   uuid        NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_file_tags PRIMARY KEY (file_id, tag_id),
    CONSTRAINT fk_file_tags_file FOREIGN KEY (file_id, owner_id)
        REFERENCES archived_files (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT fk_file_tags_tag FOREIGN KEY (tag_id, owner_id)
        REFERENCES tags (id, owner_id) ON DELETE CASCADE
);

CREATE INDEX ix_file_tags_tag ON file_tags (tag_id);
CREATE INDEX ix_file_tags_owner_file ON file_tags (owner_id, file_id);
CREATE INDEX ix_archived_files_owner_favorite
    ON archived_files (owner_id, status, archived_at DESC) WHERE favorite;
