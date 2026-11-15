package app.trashbin.activity.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** One entry of a file's activity timeline. {@code details} holds small, non-sensitive values. */
public record FileEventView(
        UUID id, String type, Instant occurredAt, Map<String, Object> details) {}
