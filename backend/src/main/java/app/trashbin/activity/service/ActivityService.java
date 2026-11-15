package app.trashbin.activity.service;

import app.trashbin.activity.domain.FileEvent;
import app.trashbin.activity.domain.FileEventType;
import app.trashbin.activity.dto.FileEventView;
import app.trashbin.activity.repository.FileEventRepository;
import app.trashbin.common.dto.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records file activity and serves the timeline. Recording joins the caller's transaction so the
 * event and the change commit together.
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

    /**
     * Newest first. The caller (archive module) has verified that {@code fileId} belongs to {@code
     * ownerId}; the query is owner-scoped regardless.
     */
    @Transactional(readOnly = true)
    public PageResponse<FileEventView> timeline(UUID ownerId, UUID fileId, int page, int size) {
        PageRequest request =
                PageRequest.of(
                        page, size, Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by("id")));
        return PageResponse.of(
                events.findByFileIdAndOwnerId(fileId, ownerId, request),
                e ->
                        new FileEventView(
                                e.getId(),
                                e.getEventType().name(),
                                e.getOccurredAt(),
                                e.getDetails()));
    }
}
