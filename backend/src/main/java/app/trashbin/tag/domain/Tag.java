package app.trashbin.tag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A user's label. Names are unique per owner, compared in normalized (lower-case) form. */
@Entity
@Table(name = "tags")
public class Tag {

    @Id @GeneratedValue @UuidGenerator private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "name_normalized", nullable = false, length = 50)
    private String nameNormalized;

    @Column(length = 7)
    private String color;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Tag() {}

    public Tag(UUID ownerId, TagName name, String color, Instant now) {
        this.ownerId = ownerId;
        this.name = name.display();
        this.nameNormalized = name.normalized();
        this.color = color;
        this.createdAt = now;
    }

    public void rename(TagName newName) {
        this.name = newName.display();
        this.nameNormalized = newName.normalized();
    }

    public void recolor(String newColor) {
        this.color = newColor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public String getNameNormalized() {
        return nameNormalized;
    }

    public String getColor() {
        return color;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Tag tag && id != null && id.equals(tag.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
