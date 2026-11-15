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
import app.trashbin.config.UploadProperties;
import app.trashbin.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The transactional half of an upload: quota, duplicate policy, row and activity event. */
@Service
public class ArchiveWriter {

    private final ArchivedFileRepository files;
    private final UserService users;
    private final ActivityService activity;
    private final UploadProperties properties;
    private final Clock clock;

    public ArchiveWriter(
            ArchivedFileRepository files,
            UserService users,
            ActivityService activity,
            UploadProperties properties,
            Clock clock) {
        this.files = files;
        this.users = users;
        this.activity = activity;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public FileDetail persist(NewArchivedFile spec, boolean allowDuplicate) {
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
                                throw new DuplicateFileException(FileMapper.toSummary(existing));
                            });
        }

        ArchivedFile file = files.saveAndFlush(new ArchivedFile(spec, Instant.now(clock)));
        activity.record(file.getId(), file.getOwnerId(), FileEventType.ARCHIVED, Map.of());
        return FileMapper.toDetail(file);
    }
}
