-- Full-text search (docs/DATABASE_DESIGN.md section 6).
--
-- file_search_vector() is the single definition of what is searchable and how it is weighted:
--   A  display name and original filename (dots, dashes, underscores split words)
--   B  tag names and notes
--   C  "why I kept it"
-- The application calls it after every change to those inputs (SearchIndexer); this migration
-- uses it to backfill existing rows.

CREATE OR REPLACE FUNCTION file_search_vector(p_file_id uuid) RETURNS tsvector
    LANGUAGE sql STABLE AS
$$
SELECT setweight(to_tsvector('english'::regconfig,
                             translate(f.display_name || ' ' || f.original_filename, '._-', '   ')), 'A')
    || setweight(to_tsvector('english'::regconfig, coalesce(
           (SELECT string_agg(t.name, ' ')
            FROM file_tags ft
            JOIN tags t ON t.id = ft.tag_id
            WHERE ft.file_id = f.id), '')), 'B')
    || setweight(to_tsvector('english'::regconfig, coalesce(f.notes, '')), 'B')
    || setweight(to_tsvector('english'::regconfig, coalesce(f.context_note, '')), 'C')
FROM archived_files f
WHERE f.id = p_file_id
$$;

UPDATE archived_files SET search_vector = file_search_vector(id);

CREATE INDEX ix_archived_files_search ON archived_files USING gin (search_vector);
-- Partial and misspelled file-name matches (ILIKE and the word-similarity operator <%).
CREATE INDEX ix_archived_files_name_trgm ON archived_files USING gin (display_name gin_trgm_ops);
