package app.trashbin.archive.repository;

import app.trashbin.archive.domain.ArchivedFile;
import app.trashbin.common.persistence.OwnedEntityRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every query is scoped by owner, except the storage-catalog queries used for reconciliation.
 * Listing and search go through {@link FileSearchDao}.
 */
public interface ArchivedFileRepository
        extends JpaRepository<ArchivedFile, UUID>, OwnedEntityRepository {

    Optional<ArchivedFile> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<ArchivedFile> findAllByOwnerIdAndIdIn(UUID ownerId, Collection<UUID> ids);

    Optional<ArchivedFile> findFirstByOwnerIdAndSha256OrderByArchivedAtAsc(
            UUID ownerId, String sha256);

    @Query("select coalesce(sum(f.sizeBytes), 0) from ArchivedFile f where f.ownerId = :ownerId")
    long sumSizeByOwnerId(@Param("ownerId") UUID ownerId);

    boolean existsByStorageKey(UUID storageKey);

    /**
     * Records an access without touching the version (a bulk update bypasses optimistic locking),
     * so opening a file never makes someone's pending edit conflict.
     */
    @Modifying
    @Query(
            "update ArchivedFile f set f.lastAccessedAt = :now"
                    + " where f.id = :id and f.ownerId = :ownerId")
    int markAccessed(
            @Param("id") UUID id, @Param("ownerId") UUID ownerId, @Param("now") Instant now);

    @Query(
            "select new app.trashbin.archive.repository.StorageRef(f.id, f.storageKey)"
                    + " from ArchivedFile f")
    List<StorageRef> findAllStorageRefs();
}
