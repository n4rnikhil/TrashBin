package app.trashbin.archive.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The archive's search and filter query (docs/DATABASE_DESIGN.md section 6). Native SQL because it
 * combines full-text ranking, trigram matching and filters over the tag and collection link tables
 * in one statement. Every value is a bound parameter; the only text spliced into SQL comes from the
 * fixed {@link Sort} enum. Results are always restricted to one owner.
 */
@Repository
public class FileSearchDao {

    /** Whitelisted orderings. {@code RELEVANCE} requires a query text. */
    public enum Sort {
        ARCHIVED_DESC("f.archived_at DESC"),
        ARCHIVED_ASC("f.archived_at ASC"),
        NAME_ASC("lower(f.display_name) ASC"),
        NAME_DESC("lower(f.display_name) DESC"),
        SIZE_DESC("f.size_bytes DESC"),
        SIZE_ASC("f.size_bytes ASC"),
        ACCESSED_DESC("f.last_accessed_at DESC NULLS LAST"),
        ACCESSED_ASC("f.last_accessed_at ASC NULLS LAST"),
        RELEVANCE(
                "(ts_rank_cd(f.search_vector, websearch_to_tsquery('english', :q))"
                        + " + coalesce((SELECT ts_rank_cd(c.content_tsv,"
                        + " websearch_to_tsquery('english', :q)) FROM file_text_content c"
                        + " WHERE c.file_id = f.id), 0) * 0.5"
                        + " + word_similarity(:q, f.display_name)) DESC");

        private final String sql;

        Sort(String sql) {
            this.sql = sql;
        }
    }

    /**
     * Search criteria. Null means "no filter"; {@code tagIds} must all match (AND).
     *
     * @param archivedFrom inclusive lower bound on archive time
     * @param archivedTo exclusive upper bound on archive time
     */
    public record Criteria(
            UUID ownerId,
            String status,
            String text,
            Boolean favorite,
            String typeGroup,
            List<UUID> tagIds,
            UUID collectionId,
            Instant archivedFrom,
            Instant archivedTo) {}

    public record Result(List<UUID> ids, long total) {}

    private static final int STATEMENT_TIMEOUT_MS = 5000;
    private static final double WORD_SIMILARITY = 0.5;

    private final NamedParameterJdbcTemplate jdbc;

    public FileSearchDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Result search(Criteria criteria, Sort sort, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(criteria, params);

        // Transaction-local settings: guard against pathological queries, and accept one-letter
        // typos in file names (pg_trgm's default of 0.6 rejects e.g. "aprtment" for "Apartment").
        jdbc.getJdbcTemplate().execute("SET LOCAL statement_timeout = " + STATEMENT_TIMEOUT_MS);
        jdbc.getJdbcTemplate()
                .execute("SET LOCAL pg_trgm.word_similarity_threshold = " + WORD_SIMILARITY);

        Long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM archived_files f WHERE " + where, params, Long.class);
        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<UUID> ids =
                jdbc.queryForList(
                        "SELECT f.id FROM archived_files f WHERE "
                                + where
                                + " ORDER BY "
                                + sort.sql
                                + ", f.archived_at DESC, f.id LIMIT :limit OFFSET :offset",
                        params,
                        UUID.class);
        return new Result(ids, total == null ? 0 : total);
    }

    private static String where(Criteria c, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder("f.owner_id = :owner AND f.status = :status");
        params.addValue("owner", c.ownerId()).addValue("status", c.status());

        if (c.favorite() != null) {
            sql.append(" AND f.favorite = :favorite");
            params.addValue("favorite", c.favorite());
        }
        if (c.typeGroup() != null) {
            sql.append(" AND f.type_group = :typeGroup");
            params.addValue("typeGroup", c.typeGroup());
        }
        if (c.archivedFrom() != null) {
            sql.append(" AND f.archived_at >= :from");
            params.addValue("from", c.archivedFrom().atOffset(ZoneOffset.UTC));
        }
        if (c.archivedTo() != null) {
            sql.append(" AND f.archived_at < :to");
            params.addValue("to", c.archivedTo().atOffset(ZoneOffset.UTC));
        }
        if (c.collectionId() != null) {
            sql.append(
                    " AND EXISTS (SELECT 1 FROM collection_files cf WHERE cf.file_id = f.id"
                            + " AND cf.collection_id = :collection AND cf.owner_id = :owner)");
            params.addValue("collection", c.collectionId());
        }
        for (int i = 0; i < c.tagIds().size(); i++) {
            sql.append(
                    " AND EXISTS (SELECT 1 FROM file_tags ft WHERE ft.file_id = f.id"
                            + " AND ft.tag_id = :tag"
                            + i
                            + " AND ft.owner_id = :owner)");
            params.addValue("tag" + i, c.tagIds().get(i));
        }
        if (c.text() != null) {
            sql.append(
                    " AND (f.search_vector @@ websearch_to_tsquery('english', :q)"
                            + " OR f.display_name ILIKE :like ESCAPE '\\'"
                            + " OR :q <% f.display_name"
                            + " OR EXISTS (SELECT 1 FROM file_text_content c WHERE c.file_id = f.id"
                            + " AND c.content_tsv @@ websearch_to_tsquery('english', :q)))");
            params.addValue("q", c.text()).addValue("like", "%" + escapeLike(c.text()) + "%");
        }
        return sql.toString();
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
