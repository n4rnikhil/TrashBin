package app.trashbin.collection.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Collection membership links. Plain SQL, always scoped by owner. */
@Repository
public class CollectionFileDao {

    /** A collection a file belongs to. */
    public record Membership(UUID collectionId, String name) {}

    private final NamedParameterJdbcTemplate jdbc;

    public CollectionFileDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void replaceCollectionsOfFile(
            UUID ownerId, UUID fileId, Collection<UUID> collectionIds, Instant now) {
        MapSqlParameterSource base =
                new MapSqlParameterSource().addValue("owner", ownerId).addValue("file", fileId);
        jdbc.update(
                "delete from collection_files where owner_id = :owner and file_id = :file", base);
        if (collectionIds.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] rows =
                collectionIds.stream()
                        .map(
                                id ->
                                        new MapSqlParameterSource()
                                                .addValue("collection", id)
                                                .addValue("file", fileId)
                                                .addValue("owner", ownerId)
                                                .addValue("now", now.atOffset(ZoneOffset.UTC)))
                        .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(
                "insert into collection_files (collection_id, file_id, owner_id, added_at)"
                        + " values (:collection, :file, :owner, :now)",
                rows);
    }

    public List<Membership> collectionsOfFile(UUID ownerId, UUID fileId) {
        return jdbc.query(
                """
                select c.id, c.name
                from collection_files cf
                join collections c on c.id = cf.collection_id and c.owner_id = cf.owner_id
                where cf.owner_id = :owner and cf.file_id = :file
                order by c.name_normalized
                """,
                new MapSqlParameterSource().addValue("owner", ownerId).addValue("file", fileId),
                (rs, i) -> new Membership(rs.getObject(1, UUID.class), rs.getString(2)));
    }

    public Map<UUID, Long> fileCounts(UUID ownerId) {
        Map<UUID, Long> counts = new HashMap<>();
        jdbc.query(
                "select collection_id, count(*) from collection_files where owner_id = :owner"
                        + " group by collection_id",
                new MapSqlParameterSource("owner", ownerId),
                rs -> {
                    counts.put(rs.getObject(1, UUID.class), rs.getLong(2));
                });
        return counts;
    }
}
