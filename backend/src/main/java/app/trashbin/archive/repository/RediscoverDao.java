package app.trashbin.archive.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only queries behind "Rediscover" (FR-M1 – FR-M3). Archived files of one owner only. */
@Repository
public class RediscoverDao {

    private final NamedParameterJdbcTemplate jdbc;

    public RediscoverDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Files archived — or originating — on {@code month}/one of {@code days} in an earlier year
     * than {@code year}. Dates are compared in UTC.
     */
    public List<UUID> onThisDay(UUID ownerId, int month, List<Integer> days, int year, int limit) {
        return jdbc.queryForList(
                """
                select id from archived_files
                where owner_id = :owner and status = 'ARCHIVED'
                  and (
                    (extract(month from archived_at at time zone 'UTC') = :month
                     and extract(day from archived_at at time zone 'UTC') in (:days)
                     and extract(year from archived_at at time zone 'UTC') < :year)
                    or
                    (extract(month from origin_date) = :month
                     and extract(day from origin_date) in (:days)
                     and extract(year from origin_date) < :year)
                  )
                order by coalesce(origin_date, (archived_at at time zone 'UTC')::date) desc, id
                limit :limit
                """,
                new MapSqlParameterSource()
                        .addValue("owner", ownerId)
                        .addValue("month", month)
                        .addValue("days", days)
                        .addValue("year", year)
                        .addValue("limit", limit),
                UUID.class);
    }

    /** Archived before {@code cutoff} and not opened since; the longest-untouched first. */
    public List<UUID> forgotten(UUID ownerId, Instant cutoff, int limit) {
        return jdbc.queryForList(
                """
                select id from archived_files
                where owner_id = :owner and status = 'ARCHIVED' and archived_at < :cutoff
                  and (last_accessed_at is null or last_accessed_at < :cutoff)
                order by coalesce(last_accessed_at, archived_at), id
                limit :limit
                """,
                new MapSqlParameterSource()
                        .addValue("owner", ownerId)
                        .addValue("cutoff", cutoff.atOffset(ZoneOffset.UTC))
                        .addValue("limit", limit),
                UUID.class);
    }

    public List<UUID> random(UUID ownerId, int limit) {
        return jdbc.queryForList(
                "select id from archived_files where owner_id = :owner and status = 'ARCHIVED'"
                        + " order by random() limit :limit",
                new MapSqlParameterSource().addValue("owner", ownerId).addValue("limit", limit),
                UUID.class);
    }
}
