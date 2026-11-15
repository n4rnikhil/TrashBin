package app.trashbin.archive.service;

import app.trashbin.activity.dto.FileEventView;
import app.trashbin.activity.service.ActivityService;
import app.trashbin.archive.domain.ArchivedFile;
import app.trashbin.archive.domain.FileStatus;
import app.trashbin.archive.domain.TypeGroup;
import app.trashbin.archive.dto.FileDetail;
import app.trashbin.archive.dto.FileListQuery;
import app.trashbin.archive.dto.FileSummary;
import app.trashbin.archive.mapper.FileMapper;
import app.trashbin.archive.repository.ArchivedFileRepository;
import app.trashbin.archive.repository.FileSearchDao;
import app.trashbin.archive.repository.FileSearchDao.Criteria;
import app.trashbin.archive.repository.FileSearchDao.Sort;
import app.trashbin.collection.service.CollectionService;
import app.trashbin.common.dto.PageResponse;
import app.trashbin.common.error.InvalidRequestException;
import app.trashbin.common.error.ResourceNotFoundException;
import app.trashbin.common.security.AuthenticatedUser;
import app.trashbin.tag.dto.TagDtos.TagRef;
import app.trashbin.tag.service.TagService;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the archive: list, search and filters, and file details. Always owner-scoped. */
@Service
@Transactional(readOnly = true)
public class ArchiveQueryService {

    static final int MAX_QUERY_LENGTH = 200;
    static final int MAX_TAG_FILTERS = 10;

    /** API sort ({@code field,direction}) -> whitelisted SQL ordering. */
    private static final Map<String, Sort> SORTS =
            Map.ofEntries(
                    Map.entry("archivedAt,desc", Sort.ARCHIVED_DESC),
                    Map.entry("archivedAt,asc", Sort.ARCHIVED_ASC),
                    Map.entry("displayName,asc", Sort.NAME_ASC),
                    Map.entry("displayName,desc", Sort.NAME_DESC),
                    Map.entry("sizeBytes,desc", Sort.SIZE_DESC),
                    Map.entry("sizeBytes,asc", Sort.SIZE_ASC),
                    Map.entry("lastAccessedAt,desc", Sort.ACCESSED_DESC),
                    Map.entry("lastAccessedAt,asc", Sort.ACCESSED_ASC),
                    Map.entry("relevance", Sort.RELEVANCE));

    private final ArchivedFileRepository files;
    private final FileSearchDao search;
    private final TagService tags;
    private final CollectionService collections;
    private final ActivityService activity;

    public ArchiveQueryService(
            ArchivedFileRepository files,
            FileSearchDao search,
            TagService tags,
            CollectionService collections,
            ActivityService activity) {
        this.files = files;
        this.search = search;
        this.tags = tags;
        this.collections = collections;
        this.activity = activity;
    }

    public PageResponse<FileSummary> list(AuthenticatedUser caller, FileListQuery query) {
        String text = query.q() == null || query.q().isBlank() ? null : query.q().strip();
        if (text != null && text.length() > MAX_QUERY_LENGTH) {
            throw new InvalidRequestException(
                    "Search text can be at most " + MAX_QUERY_LENGTH + " characters.");
        }
        List<UUID> tagIds = query.tagIds() == null ? List.of() : query.tagIds();
        if (tagIds.size() > MAX_TAG_FILTERS) {
            throw new InvalidRequestException(
                    "Filter by at most " + MAX_TAG_FILTERS + " tags at a time.");
        }
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new InvalidRequestException("The start date must not be after the end date.");
        }

        Criteria criteria =
                new Criteria(
                        caller.id(),
                        parseStatus(query.status()).name(),
                        text,
                        query.favorite(),
                        parseTypeGroup(query.typeGroup()),
                        tagIds,
                        query.collectionId(),
                        query.from() == null ? null : startOfDay(query.from()),
                        query.to() == null ? null : startOfDay(query.to().plusDays(1)));
        FileSearchDao.Result result =
                search.search(
                        criteria,
                        parseSort(query.sort(), text != null),
                        query.page(),
                        query.size());

        // Load the page's rows and tags in two queries, preserving the search order.
        Map<UUID, ArchivedFile> byId =
                files.findAllByOwnerIdAndIdIn(caller.id(), result.ids()).stream()
                        .collect(Collectors.toMap(ArchivedFile::getId, Function.identity()));
        Map<UUID, List<TagRef>> tagsByFile = tags.tagsForFiles(caller.id(), result.ids());
        List<FileSummary> content =
                result.ids().stream()
                        .map(byId::get)
                        .filter(f -> f != null)
                        .map(
                                f ->
                                        FileMapper.toSummary(
                                                f, tagsByFile.getOrDefault(f.getId(), List.of())))
                        .toList();
        int totalPages = (int) ((result.total() + query.size() - 1) / query.size());
        return new PageResponse<>(content, query.page(), query.size(), result.total(), totalPages);
    }

    public FileDetail get(AuthenticatedUser caller, UUID fileId) {
        ArchivedFile file =
                files.findByIdAndOwnerId(fileId, caller.id())
                        .orElseThrow(ResourceNotFoundException::new);
        return FileMapper.toDetail(
                file,
                tags.tagsForFiles(caller.id(), List.of(fileId)).get(fileId),
                collections.collectionsOfFile(caller.id(), fileId));
    }

    /** The activity timeline of one of the caller's files. */
    public PageResponse<FileEventView> timeline(
            AuthenticatedUser caller, UUID fileId, int page, int size) {
        if (files.findByIdAndOwnerId(fileId, caller.id()).isEmpty()) {
            throw new ResourceNotFoundException();
        }
        return activity.timeline(caller.id(), fileId, page, size);
    }

    private static Instant startOfDay(java.time.LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static FileStatus parseStatus(String status) {
        try {
            return FileStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Unknown status. Use ARCHIVED or DISCARDED.");
        }
    }

    private static String parseTypeGroup(String typeGroup) {
        if (typeGroup == null || typeGroup.isBlank()) {
            return null;
        }
        try {
            return TypeGroup.valueOf(typeGroup.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Unknown type group.");
        }
    }

    /** Defaults: best match when searching, otherwise newest first. */
    static Sort parseSort(String sort, boolean hasText) {
        if (sort == null || sort.isBlank()) {
            return hasText ? Sort.RELEVANCE : Sort.ARCHIVED_DESC;
        }
        Sort parsed = SORTS.get(sort.strip().replace(" ", ""));
        if (parsed == null) {
            throw new InvalidRequestException(
                    "Unsupported sort. Use archivedAt, displayName, sizeBytes or lastAccessedAt"
                            + " with ,asc or ,desc — or relevance when searching.");
        }
        if (parsed == Sort.RELEVANCE && !hasText) {
            throw new InvalidRequestException("Sorting by relevance needs search text (q).");
        }
        return parsed;
    }
}
