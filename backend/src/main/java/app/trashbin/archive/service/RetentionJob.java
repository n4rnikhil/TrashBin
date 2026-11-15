package app.trashbin.archive.service;

import app.trashbin.config.LifecycleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily purge of discarded files whose retention period has ended (FR-L4). */
@Component
public class RetentionJob {

    static final int BATCH_SIZE = 100;
    private static final int MAX_BATCHES_PER_RUN = 100;
    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final LifecycleService lifecycle;
    private final LifecycleProperties properties;

    public RetentionJob(LifecycleService lifecycle, LifecycleProperties properties) {
        this.lifecycle = lifecycle;
        this.properties = properties;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledRun() {
        run();
    }

    /** Purges in batches, each its own transaction. Returns the number of files purged. */
    public int run() {
        if (properties.retentionDays() == 0) {
            return 0; // automatic purge disabled
        }
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int purged = lifecycle.purgeExpired(BATCH_SIZE);
            total += purged;
            if (purged < BATCH_SIZE) {
                break;
            }
        }
        log.info("Retention run purged {} discarded file(s)", total);
        return total;
    }
}
