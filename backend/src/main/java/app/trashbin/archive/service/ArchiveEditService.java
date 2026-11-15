package app.trashbin.archive.service;

import app.trashbin.activity.domain.FileEventType;
import app.trashbin.activity.service.ActivityService;
import app.trashbin.archive.domain.ArchivedFile;
import app.trashbin.archive.dto.FileDetail;
import app.trashbin.archive.dto.FileEditRequests.UpdateFileRequest;
import app.trashbin.archive.mapper.FileMapper;
import app.trashbin.archive.repository.ArchivedFileRepository;
import app.trashbin.collection.service.CollectionService;
import app.trashbin.common.error.InvalidRequestException;
import app.trashbin.common.error.ResourceNotFoundException;
import app.trashbin.common.error.VersionConflictException;
import app.trashbin.common.security.AuthenticatedUser;
import app.trashbin.tag.dto.TagDtos.TagRef;
import app.trashbin.tag.service.TagService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Metadata edits: details (optimistically locked), tags, collections and the favorite flag. */
@Service
@Transactional
public class ArchiveEditService {

    private final ArchivedFileRepository files;
    private final TagService tags;
    private final CollectionService collections;
    private final SearchIndexer searchIndexer;
    private final ActivityService activity;
    private final Clock clock;

    public ArchiveEditService(
            ArchivedFileRepository files,
            TagService tags,
            CollectionService collections,
            SearchIndexer searchIndexer,
            ActivityService activity,
            Clock clock) {
        this.files = files;
        this.tags = tags;
        this.collections = collections;
        this.searchIndexer = searchIndexer;
        this.activity = activity;
        this.clock = clock;
    }

    /**
     * Applies a partial edit if {@code expectedVersion} is still current; otherwise the client is
     * looking at stale data and gets a conflict instead of silently overwriting someone's change.
     */
    public FileDetail update(
            AuthenticatedUser caller, UUID fileId, long expectedVersion, UpdateFileRequest edit) {
        ArchivedFile file = require(caller, fileId);
        if (file.getVersion() != expectedVersion) {
            throw new VersionConflictException();
        }

        String displayName = file.getDisplayName();
        if (edit.displayName() != null) {
            displayName = edit.displayName().strip();
            if (displayName.isEmpty()) {
                throw new InvalidRequestException("The name cannot be empty.");
            }
        }
        String notes = edit.notes() == null ? file.getNotes() : blankToNull(edit.notes());
        String context =
                edit.contextNote() == null
                        ? file.getContextNote()
                        : blankToNull(edit.contextNote());
        LocalDate originDate =
                edit.originDate() == null ? file.getOriginDate() : parseDate(edit.originDate());

        List<String> changed = new ArrayList<>();
        if (!Objects.equals(displayName, file.getDisplayName())) changed.add("displayName");
        if (!Objects.equals(notes, file.getNotes())) changed.add("notes");
        if (!Objects.equals(context, file.getContextNote())) changed.add("contextNote");
        if (!Objects.equals(originDate, file.getOriginDate())) changed.add("originDate");

        if (file.updateMetadata(displayName, notes, context, originDate, Instant.now(clock))) {
            files.saveAndFlush(file);
            searchIndexer.reindex(caller.id(), List.of(fileId));
            activity.record(
                    file.getId(),
                    file.getOwnerId(),
                    FileEventType.METADATA_EDITED,
                    Map.of("fields", changed));
        }
        return detail(caller, file);
    }

    public FileDetail replaceTags(AuthenticatedUser caller, UUID fileId, List<String> tagNames) {
        ArchivedFile file = require(caller, fileId);
        List<TagRef> before = tags.tagsForFiles(caller.id(), List.of(fileId)).get(fileId);
        List<TagRef> after = tags.replaceFileTags(caller.id(), fileId, tagNames);
        if (!before.equals(after)) {
            file.touch(Instant.now(clock));
            files.saveAndFlush(file);
            searchIndexer.reindex(caller.id(), List.of(fileId));
            activity.record(
                    file.getId(),
                    file.getOwnerId(),
                    FileEventType.METADATA_EDITED,
                    Map.of("fields", List.of("tags")));
        }
        return detail(caller, file);
    }

    /** Collection membership is not part of the file's own data, so the version does not move. */
    public FileDetail replaceCollections(
            AuthenticatedUser caller, UUID fileId, List<UUID> collectionIds) {
        ArchivedFile file = require(caller, fileId);
        collections.replaceCollectionsOfFile(caller.id(), fileId, collectionIds);
        return detail(caller, file);
    }

    public FileDetail setFavorite(AuthenticatedUser caller, UUID fileId, boolean favorite) {
        ArchivedFile file = require(caller, fileId);
        file.markFavorite(favorite, Instant.now(clock));
        return detail(caller, files.saveAndFlush(file));
    }

    private FileDetail detail(AuthenticatedUser caller, ArchivedFile file) {
        return FileMapper.toDetail(
                file,
                tags.tagsForFiles(caller.id(), List.of(file.getId())).get(file.getId()),
                collections.collectionsOfFile(caller.id(), file.getId()));
    }

    private ArchivedFile require(AuthenticatedUser caller, UUID fileId) {
        return files.findByIdAndOwnerId(fileId, caller.id())
                .orElseThrow(ResourceNotFoundException::new);
    }

    private static String blankToNull(String value) {
        return value.isBlank() ? null : value.strip();
    }

    private static LocalDate parseDate(String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException("The origin date is not a valid date.");
        }
    }
}
