package app.trashbin.tag.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * File/tag links. Plain SQL on purpose: the link table has no behavior worth an entity, and the
 * merge needs an INSERT ... SELECT. Every statement is scoped by owner.
 */
@Repository
public class FileTagDao {

    /** A tag attached to a file. */
    public record Link(UUID fileId, UUID tagId, String name, String color) {}

    private final NamedParameterJdbcTemplate jdbc;

    public FileTagDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void replaceLinks(UUID ownerId, UUID fileId, Collection<UUID> tagIds, Instant now) {
        jdbc.update(
                "delete from file_tags where owner_id = :owner and file_id = :file",
                new MapSqlParameterSource().addValue("owner", ownerId).addValue("file", fileId));
        if (tagIds.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] rows =
                tagIds.stream()
                        .map(
                                tagId ->
                                        new MapSqlParameterSource()
                                                .addValue("file", fileId)
                                                .addValue("tag", tagId)
                                                .addValue("owner", ownerId)
                                                .addValue("now", utc(now)))
                        .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(
                "insert into file_tags (file_id, tag_id, owner_id, created_at)"
                        + " values (:file, :tag, :owner, :now)",
                rows);
    }

    public List<Link> linksForFiles(UUID ownerId, Collection<UUID> fileIds) {
        if (fileIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                """
                select ft.file_id, t.id, t.name, t.color
                from file_tags ft
                join tags t on t.id = ft.tag_id and t.owner_id = ft.owner_id
                where ft.owner_id = :owner and ft.file_id in (:files)
                order by t.name_normalized
                """,
                new MapSqlParameterSource().addValue("owner", ownerId).addValue("files", fileIds),
                (rs, i) ->
                        new Link(
                                rs.getObject(1, UUID.class),
                                rs.getObject(2, UUID.class),
                                rs.getString(3),
                                rs.getString(4)));
    }

    public List<UUID> fileIdsWithTag(UUID ownerId, UUID tagId) {
        return jdbc.queryForList(
                "select file_id from file_tags where owner_id = :owner and tag_id = :tag",
                new MapSqlParameterSource().addValue("owner", ownerId).addValue("tag", tagId),
                UUID.class);
    }

    public Map<UUID, Long> fileCounts(UUID ownerId) {
        Map<UUID, Long> counts = new HashMap<>();
        jdbc.query(
                "select tag_id, count(*) from file_tags where owner_id = :owner group by tag_id",
                new MapSqlParameterSource("owner", ownerId),
                rs -> {
                    counts.put(rs.getObject(1, UUID.class), rs.getLong(2));
                });
        return counts;
    }

    /** Gives every file tagged {@code source} the tag {@code target} as well. */
    public void copyLinks(UUID ownerId, UUID source, UUID target, Instant now) {
        jdbc.update(
                """
                insert into file_tags (file_id, tag_id, owner_id, created_at)
                select file_id, :target, owner_id, :now
                from file_tags
                where owner_id = :owner and tag_id = :source
                on conflict do nothing
                """,
                new MapSqlParameterSource()
                        .addValue("owner", ownerId)
                        .addValue("source", source)
                        .addValue("target", target)
                        .addValue("now", utc(now)));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
