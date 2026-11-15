package app.trashbin.archive.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Extracted text. Its search vector ({@code content_tsv}) is a generated column, so storing the
 * text is all it takes to make the contents searchable.
 */
@Repository
public class FileTextDao {

    private final NamedParameterJdbcTemplate jdbc;

    public FileTextDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(UUID fileId, String content, boolean truncated, Instant extractedAt) {
        jdbc.update(
                """
                insert into file_text_content (file_id, content, truncated, extracted_at)
                values (:file, :content, :truncated, :at)
                on conflict (file_id) do update
                set content = excluded.content, truncated = excluded.truncated,
                    extracted_at = excluded.extracted_at
                """,
                new MapSqlParameterSource()
                        .addValue("file", fileId)
                        .addValue("content", content)
                        .addValue("truncated", truncated)
                        .addValue("at", extractedAt.atOffset(ZoneOffset.UTC)));
    }
}
