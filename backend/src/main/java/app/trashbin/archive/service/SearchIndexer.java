package app.trashbin.archive.service;

import app.trashbin.tag.service.TagsChangedEvent;
import java.util.Collection;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps {@code archived_files.search_vector} current. What is indexed is defined once, in the SQL
 * function {@code file_search_vector} (migration V6). Runs in the caller's transaction, after the
 * caller has flushed its changes, so the index and the data commit together.
 */
@Component
public class SearchIndexer {

    private final NamedParameterJdbcTemplate jdbc;

    public SearchIndexer(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reindex(UUID ownerId, Collection<UUID> fileIds) {
        if (fileIds.isEmpty()) {
            return;
        }
        jdbc.update(
                "update archived_files set search_vector = file_search_vector(id)"
                        + " where owner_id = :owner and id in (:ids)",
                new MapSqlParameterSource().addValue("owner", ownerId).addValue("ids", fileIds));
    }

    /** A tag rename, merge or delete changes the indexed text of every file that carried it. */
    @EventListener
    public void onTagsChanged(TagsChangedEvent event) {
        reindex(event.ownerId(), event.fileIds());
    }
}
