package app.trashbin.archive.repository;

import app.trashbin.archive.domain.ArchivedFile;
import app.trashbin.archive.domain.FileStatus;
import app.trashbin.common.persistence.OwnedEntityRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query is scoped by owner, except the storage-catalog queries used for reconciliation. */
public interface ArchivedFileRepository
        extends JpaRepository<ArchivedFile, UUID>, OwnedEntityRepository {

    Optional<ArchivedFile> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<ArchivedFile> findByOwnerIdAndStatus(UUID ownerId, FileStatus status, Pageable pageable);

    Page<ArchivedFile> findByOwnerIdAndStatusAndFavorite(
            UUID ownerId, FileStatus status, boolean favorite, Pageable pageable);

    Optional<ArchivedFile> findFirstByOwnerIdAndSha256OrderByArchivedAtAsc(
            UUID ownerId, String sha256);

    @Query("select coalesce(sum(f.sizeBytes), 0) from ArchivedFile f where f.ownerId = :ownerId")
    long sumSizeByOwnerId(@Param("ownerId") UUID ownerId);

    boolean existsByStorageKey(UUID storageKey);

    @Query(
            "select new app.trashbin.archive.repository.StorageRef(f.id, f.storageKey)"
                    + " from ArchivedFile f")
    List<StorageRef> findAllStorageRefs();
}
