package app.trashbin.collection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A named group of files ("Taxes 2019", "House purchase"). Names are unique per owner. */
@Entity
@Table(name = "collections")
public class FileCollection {

    public static final int MAX_NAME_LENGTH = 100;

    @Id @GeneratedValue @UuidGenerator private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "name_normalized", nullable = false, length = 100)
    private String nameNormalized;

    @Column private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FileCollection() {}

    public FileCollection(UUID ownerId, String name, String description, Instant now) {
        this.ownerId = ownerId;
        this.name = name;
        this.nameNormalized = normalize(name);
        this.description = description;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static String normalize(String cleanName) {
        return cleanName.toLowerCase(Locale.ROOT);
    }

    public void rename(String newName, Instant now) {
        this.name = newName;
        this.nameNormalized = normalize(newName);
        this.updatedAt = now;
    }

    public void describe(String newDescription, Instant now) {
        this.description = newDescription;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNameNormalized() {
        return nameNormalized;
    }

    public String getDescription() {
        return description;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FileCollection c && id != null && id.equals(c.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
