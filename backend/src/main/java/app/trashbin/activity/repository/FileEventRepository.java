package app.trashbin.activity.repository;

import app.trashbin.activity.domain.FileEvent;
import app.trashbin.common.persistence.OwnedEntityRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileEventRepository extends JpaRepository<FileEvent, UUID>, OwnedEntityRepository {

    List<FileEvent> findByFileIdAndOwnerIdOrderByOccurredAtDesc(UUID fileId, UUID ownerId);

    Page<FileEvent> findByFileIdAndOwnerId(UUID fileId, UUID ownerId, Pageable pageable);
}
