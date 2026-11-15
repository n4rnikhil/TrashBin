package app.trashbin.archive.service;

import app.trashbin.activity.domain.FileEventType;
import app.trashbin.activity.service.ActivityService;
import app.trashbin.archive.domain.ArchivedFile;
import app.trashbin.archive.domain.NewArchivedFile;
import app.trashbin.archive.dto.FileDetail;
import app.trashbin.archive.mapper.FileMapper;
import app.trashbin.archive.repository.ArchivedFileRepository;
import app.trashbin.archive.service.UploadExceptions.DuplicateFileException;
import app.trashbin.archive.service.UploadExceptions.QuotaExceededException;
import app.trashbin.collection.dto.CollectionDtos.CollectionRef;
import app.trashbin.collection.service.CollectionService;
import app.trashbin.config.UploadProperties;
import app.trashbin.tag.dto.TagDtos.TagRef;
import app.trashbin.tag.service.TagService;
import app.trashbin.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional half of an upload: quota, duplicate policy, row, tags, collections, search
 * index and activity event — all committed together.
 */
@Service
public class ArchiveWriter {

    private final ArchivedFileRepository files;
    private final UserService users;
    private final TagService tags;
    private final CollectionService collections;
    private final SearchIndexer searchIndexer;
    private final ActivityService activity;
    private final UploadProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ArchiveWriter(
            ArchivedFileRepository files,
            UserService users,
            TagService tags,
            CollectionService collections,
            SearchIndexer searchIndexer,
            ActivityService activity,
            UploadProperties properties,
            ApplicationEventPublisher events,
            Clock clock) {
        this.files = files;
        this.users = users;
        this.tags = tags;
        this.collections = collections;
        this.searchIndexer = searchIndexer;
        this.activity = activity;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public FileDetail persist(
            NewArchivedFile spec,
            boolean allowDuplicate,
            List<String> tagNames,
            List<UUID> collectionIds) {
        // Serialize this user's uploads so two concurrent ones cannot both pass the quota check.
        users.lockAccount(spec.ownerId());

        long used = files.sumSizeByOwnerId(spec.ownerId());
        if (used + spec.sizeBytes() > properties.userQuota().toBytes()) {
            throw new QuotaExceededException();
        }
        if (!allowDuplicate) {
            files.findFirstByOwnerIdAndSha256OrderByArchivedAtAsc(spec.ownerId(), spec.sha256())
                    .ifPresent(
                            existing -> {
                                throw new DuplicateFileException(
                                        FileMapper.toSummary(existing, List.of()));
                            });
        }

        ArchivedFile file = files.saveAndFlush(new ArchivedFile(spec, Instant.now(clock)));
        UUID owner = file.getOwnerId();
        List<TagRef> fileTags =
                tagNames.isEmpty()
                        ? List.of()
                        : tags.replaceFileTags(owner, file.getId(), tagNames);
        List<CollectionRef> fileCollections =
                collectionIds.isEmpty()
                        ? List.of()
                        : collections.replaceCollectionsOfFile(owner, file.getId(), collectionIds);
        searchIndexer.reindex(owner, List.of(file.getId()));
        activity.record(file.getId(), owner, FileEventType.ARCHIVED, Map.of());
        // Picked up after commit by the text extractor (M4b).
        events.publishEvent(new FileArchivedEvent(owner, file.getId()));
        return FileMapper.toDetail(file, fileTags, fileCollections);
    }
}
