-- Collections: named, flat, many-to-many groupings of files (docs/DATABASE_DESIGN.md 3.6, 3.7).
-- Like file_tags, collection_files uses composite foreign keys so a file can only join a
-- collection of the same owner.

CREATE TABLE collections (
    id              uuid         PRIMARY KEY,
    owner_id        uuid         NOT NULL REFERENCES users (id),
    name            varchar(100) NOT NULL,
    name_normalized varchar(100) NOT NULL,
    description     text,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT uq_collections_owner_name UNIQUE (owner_id, name_normalized),
    CONSTRAINT uq_collections_id_owner UNIQUE (id, owner_id),
    CONSTRAINT ck_collections_name CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT ck_collections_description CHECK (char_length(description) <= 2000)
);

CREATE TABLE collection_files (
    collection_id uuid        NOT NULL,
    file_id       uuid        NOT NULL,
    owner_id      uuid        NOT NULL,
    added_at      timestamptz NOT NULL,
    CONSTRAINT pk_collection_files PRIMARY KEY (collection_id, file_id),
    CONSTRAINT fk_collection_files_collection FOREIGN KEY (collection_id, owner_id)
        REFERENCES collections (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT fk_collection_files_file FOREIGN KEY (file_id, owner_id)
        REFERENCES archived_files (id, owner_id) ON DELETE CASCADE
);

CREATE INDEX ix_collection_files_file ON collection_files (file_id);
CREATE INDEX ix_collection_files_owner_collection ON collection_files (owner_id, collection_id);
