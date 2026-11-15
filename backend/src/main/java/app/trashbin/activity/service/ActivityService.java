package app.trashbin.activity.service;

import app.trashbin.activity.domain.FileEvent;
import app.trashbin.activity.domain.FileEventType;
import app.trashbin.activity.repository.FileEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records file activity. Joins the caller's transaction so the event and the change commit
 * together.
 */
@Service
public class ActivityService {

    private final FileEventRepository events;
    private final Clock clock;

    public ActivityService(FileEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID fileId, UUID ownerId, FileEventType type, Map<String, Object> details) {
        events.save(new FileEvent(fileId, ownerId, type, details, Instant.now(clock)));
    }
}
