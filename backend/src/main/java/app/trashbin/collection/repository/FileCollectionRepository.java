package app.trashbin.collection.repository;

import app.trashbin.collection.domain.FileCollection;
import app.trashbin.common.persistence.OwnedEntityRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileCollectionRepository
        extends JpaRepository<FileCollection, UUID>, OwnedEntityRepository {

    Optional<FileCollection> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<FileCollection> findAllByOwnerIdOrderByNameNormalizedAsc(UUID ownerId);

    List<FileCollection> findAllByOwnerIdAndIdIn(UUID ownerId, Collection<UUID> ids);

    boolean existsByOwnerIdAndNameNormalized(UUID ownerId, String nameNormalized);

    boolean existsByOwnerIdAndNameNormalizedAndIdNot(UUID ownerId, String name, UUID excludedId);
}
