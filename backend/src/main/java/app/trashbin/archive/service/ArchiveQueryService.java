package app.trashbin.archive.service;

import app.trashbin.archive.domain.ArchivedFile;
import app.trashbin.archive.domain.FileStatus;
import app.trashbin.archive.dto.FileDetail;
import app.trashbin.archive.dto.FileSummary;
import app.trashbin.archive.mapper.FileMapper;
import app.trashbin.archive.repository.ArchivedFileRepository;
import app.trashbin.common.dto.PageResponse;
import app.trashbin.common.error.InvalidRequestException;
import app.trashbin.common.error.ResourceNotFoundException;
import app.trashbin.common.security.AuthenticatedUser;
import app.trashbin.tag.dto.TagDtos.TagRef;
import app.trashbin.tag.service.TagService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the archive. Every query is scoped to the caller. */
@Service
@Transactional(readOnly = true)
public class ArchiveQueryService {

    /** API sort field -> entity property. Anything else is rejected. */
    private static final Map<String, String> SORTABLE =
            Map.of(
                    "archivedAt", "archivedAt",
                    "displayName", "displayName",
                    "sizeBytes", "sizeBytes",
                    "lastAccessedAt", "lastAccessedAt");

    private final ArchivedFileRepository files;
    private final TagService tags;

    public ArchiveQueryService(ArchivedFileRepository files, TagService tags) {
        this.files = files;
        this.tags = tags;
    }

    /**
     * @param favorite when non-null, only files with this favorite flag
     */
    public PageResponse<FileSummary> list(
            AuthenticatedUser caller,
            String status,
            Boolean favorite,
            int page,
            int size,
            String sort) {
        PageRequest request = PageRequest.of(page, size, parseSort(sort));
        FileStatus fileStatus = parseStatus(status);
        Page<ArchivedFile> result =
                favorite == null
                        ? files.findByOwnerIdAndStatus(caller.id(), fileStatus, request)
                        : files.findByOwnerIdAndStatusAndFavorite(
                                caller.id(), fileStatus, favorite, request);
        // One query for the tags of the whole page (no N+1).
        Map<UUID, List<TagRef>> tagsByFile =
                tags.tagsForFiles(
                        caller.id(),
                        result.getContent().stream().map(ArchivedFile::getId).toList());
        return PageResponse.of(
                result,
                f -> FileMapper.toSummary(f, tagsByFile.getOrDefault(f.getId(), List.of())));
    }

    public FileDetail get(AuthenticatedUser caller, UUID fileId) {
        ArchivedFile file =
                files.findByIdAndOwnerId(fileId, caller.id())
                        .orElseThrow(ResourceNotFoundException::new);
        return FileMapper.toDetail(
                file, tags.tagsForFiles(caller.id(), List.of(fileId)).get(fileId));
    }

    private static FileStatus parseStatus(String status) {
        try {
            return FileStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Unknown status. Use ARCHIVED or DISCARDED.");
        }
    }

    /** {@code field,asc|desc}; ties are broken by id so pages never overlap. */
    static Sort parseSort(String sort) {
        String[] parts = sort.split(",", -1);
        String property = SORTABLE.get(parts[0].strip());
        if (property == null || parts.length > 2) {
            throw new InvalidRequestException(
                    "Unsupported sort. Use one of " + SORTABLE.keySet() + " with asc or desc.");
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length == 2) {
            direction =
                    Sort.Direction.fromOptionalString(parts[1].strip())
                            .orElseThrow(
                                    () ->
                                            new InvalidRequestException(
                                                    "Sort direction must be asc or desc."));
        }
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
