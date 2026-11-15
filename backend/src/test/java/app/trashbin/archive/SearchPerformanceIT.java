package app.trashbin.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.trashbin.support.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * NFR-P1: with 10,000 files for one user, list and search answer in under 300 ms at the 95th
 * percentile (measured end to end through the controller, on a local database).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchPerformanceIT {

    private static final int FILES = 10_000;
    private static final int RUNS_PER_QUERY = 10;
    private static final long P95_BUDGET_MS = 300;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void listAndSearchStayWithinBudgetAtTenThousandFiles() throws Exception {
        TestData.resetDatabase(jdbc);
        String bearer = TestData.bearerFor(mockMvc, json, "perf@example.com");
        UUID owner = jdbc.queryForObject("select id from users", UUID.class);
        seed(owner);

        List<String> queries =
                List.of(
                        "",
                        "sort=displayName,asc&page=40",
                        "q=invoice",
                        "q=invoce", // typo
                        "q=report%202019",
                        "typeGroup=DOCUMENT&from=2023-01-01",
                        "favorite=true",
                        "tag=" + jdbc.queryForObject("select id from tags limit 1", UUID.class),
                        "q=holiday&tag="
                                + jdbc.queryForObject("select id from tags limit 1", UUID.class));

        List<Long> timings = new ArrayList<>();
        for (String query : queries) {
            fetch(bearer, query); // warm-up
            for (int i = 0; i < RUNS_PER_QUERY; i++) {
                long start = System.nanoTime();
                fetch(bearer, query);
                timings.add((System.nanoTime() - start) / 1_000_000);
            }
        }
        Collections.sort(timings);
        long p95 = timings.get((int) Math.ceil(timings.size() * 0.95) - 1);
        System.out.printf(
                "Search performance over %d files: p50=%d ms, p95=%d ms, max=%d ms%n",
                FILES, timings.get(timings.size() / 2), p95, timings.get(timings.size() - 1));
        assertThat(p95).isLessThan(P95_BUDGET_MS);
    }

    private void fetch(String bearer, String query) throws Exception {
        mockMvc.perform(get("/api/v1/files?" + query).header("Authorization", bearer))
                .andExpect(status().isOk());
    }

    /** Realistic-looking rows straight in SQL; blobs are not needed for list/search. */
    private void seed(UUID owner) {
        jdbc.update(
                """
                insert into archived_files (id, owner_id, original_filename, display_name,
                    media_type, type_group, size_bytes, sha256, storage_key, status,
                    extraction_status, notes, favorite, archived_at, created_at, updated_at)
                select gen_random_uuid(), ?,
                    w.word || '_' || n || '.pdf',
                    initcap(w.word) || ' ' || (2015 + n % 10) || ' #' || n,
                    'application/pdf',
                    case when n % 3 = 0 then 'IMAGE' else 'DOCUMENT' end,
                    1000 + n,
                    lpad(to_hex(n), 64, '0'),
                    gen_random_uuid(), 'ARCHIVED', 'PENDING',
                    'Notes about ' || w.word || ' number ' || n,
                    n % 50 = 0,
                    now() - (n || ' hours')::interval, now(), now()
                from generate_series(1, ?) n
                cross join lateral (
                    select (array['invoice','report','holiday','lease','receipt','warranty',
                                  'payslip','contract','manual','letter'])[1 + n % 10] as word) w
                """,
                owner, FILES);
        jdbc.update(
                "insert into tags (id, owner_id, name, name_normalized, created_at)"
                        + " select gen_random_uuid(), ?, t, t, now()"
                        + " from unnest(array['work','home','tax','travel']) t",
                owner);
        jdbc.update(
                "insert into file_tags (file_id, tag_id, owner_id, created_at)"
                        + " select f.id, t.id, f.owner_id, now()"
                        + " from archived_files f join tags t on t.owner_id = f.owner_id"
                        + " where f.owner_id = ? and abs(hashtext(f.id::text || t.name)) % 4 = 0",
                owner);
        jdbc.update(
                "update archived_files set search_vector = file_search_vector(id) where owner_id = ?",
                owner);
        jdbc.execute("analyze archived_files");
        jdbc.execute("analyze file_tags");
    }
}
