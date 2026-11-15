package app.trashbin.archive.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A file the user chose to keep. The original bytes live in the blob store under {@code
 * storageKey}; this row holds everything else. The search vector column is maintained by SQL and is
 * deliberately not mapped.
 */
@Entity
@Table(name = "archived_files")
public class ArchivedFile {

    @Id @GeneratedValue @UuidGenerator private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "original_filename", nullable = false, updatable = false)
    private String originalFilename;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "media_type", nullable = false, updatable = false, length = 127)
    private String mediaType;

    @Column(name = "declared_media_type", updatable = false)
    private String declaredMediaType;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_group", nullable = false, length = 20)
    private TypeGroup typeGroup;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private UUID storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 20)
    private ExtractionStatus extractionStatus;

    @Column private String notes;

    @Column(name = "context_note")
    private String contextNote;

    @Column(name = "origin_date")
    private LocalDate originDate;

    @Column(nullable = false)
    private boolean favorite;

    @Column(name = "archived_at", nullable = false, updatable = false)
    private Instant archivedAt;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    @Column(name = "discarded_at")
    private Instant discardedAt;

    @Column(name = "purge_after")
    private Instant purgeAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected ArchivedFile() {}

    /** A newly archived file whose blob has already been stored and verified. */
    public ArchivedFile(NewArchivedFile spec, Instant now) {
        this.ownerId = spec.ownerId();
        this.originalFilename = spec.originalFilename();
        this.displayName = spec.displayName();
        this.mediaType = spec.mediaType();
        this.declaredMediaType = spec.declaredMediaType();
        this.typeGroup = spec.typeGroup();
        this.sizeBytes = spec.sizeBytes();
        this.sha256 = spec.sha256();
        this.storageKey = spec.storageKey();
        this.notes = spec.notes();
        this.contextNote = spec.contextNote();
        this.originDate = spec.originDate();
        this.status = FileStatus.ARCHIVED;
        this.extractionStatus = ExtractionStatus.PENDING;
        this.archivedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getMediaType() {
        return mediaType;
    }

    public TypeGroup getTypeGroup() {
        return typeGroup;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public UUID getStorageKey() {
        return storageKey;
    }

    public FileStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public String getContextNote() {
        return contextNote;
    }

    public LocalDate getOriginDate() {
        return originDate;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public Instant getDiscardedAt() {
        return discardedAt;
    }

    public Instant getPurgeAfter() {
        return purgeAfter;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ArchivedFile file && id != null && id.equals(file.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
