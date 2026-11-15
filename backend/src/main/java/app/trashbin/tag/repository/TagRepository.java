package app.trashbin.tag.repository;

import app.trashbin.common.persistence.OwnedEntityRepository;
import app.trashbin.tag.domain.Tag;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, UUID>, OwnedEntityRepository {

    Optional<Tag> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Tag> findAllByOwnerIdOrderByNameNormalizedAsc(UUID ownerId);

    List<Tag> findAllByOwnerIdAndNameNormalizedIn(UUID ownerId, Collection<String> names);

    boolean existsByOwnerIdAndNameNormalizedAndIdNot(UUID ownerId, String name, UUID excludedId);

    boolean existsByOwnerIdAndNameNormalized(UUID ownerId, String name);
}
