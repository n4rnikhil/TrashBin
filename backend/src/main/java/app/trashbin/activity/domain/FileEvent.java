package app.trashbin.activity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** Append-only activity entry for a file. Details hold small, non-sensitive values only. */
@Entity
@Table(name = "file_events")
public class FileEvent {

    @Id @GeneratedValue @UuidGenerator private UUID id;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private FileEventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> details;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected FileEvent() {}

    public FileEvent(
            UUID fileId,
            UUID ownerId,
            FileEventType eventType,
            Map<String, Object> details,
            Instant occurredAt) {
        this.fileId = fileId;
        this.ownerId = ownerId;
        this.eventType = eventType;
        this.details = Map.copyOf(details);
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public FileEventType getEventType() {
        return eventType;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FileEvent event && id != null && id.equals(event.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
