package app.trashbin.archive.service;

import app.trashbin.archive.domain.ArchivedFile;
import app.trashbin.archive.domain.FileStatus;
import app.trashbin.archive.dto.FileSummary;
import app.trashbin.archive.mapper.FileMapper;
import app.trashbin.archive.repository.ArchivedFileRepository;
import app.trashbin.archive.repository.RediscoverDao;
import app.trashbin.common.error.InvalidRequestException;
import app.trashbin.common.security.AuthenticatedUser;
import app.trashbin.config.LifecycleProperties;
import app.trashbin.config.UploadProperties;
import app.trashbin.tag.dto.TagDtos.TagRef;
import app.trashbin.tag.service.TagService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rediscovery (FR-M1 – FR-M3) and the overview statistics (FR-M5). No AI; plain queries. */
@Service
@Transactional(readOnly = true)
public class DiscoveryService {

    public record Stats(
            long archivedCount,
            long discardedCount,
            long usedBytes,
            long quotaBytes,
            int retentionDays) {}

    /** A file counts as forgotten after this long without being opened. */
    static final Duration FORGOTTEN_AFTER = Duration.ofDays(180);

    private static final int LIMIT = 12;

    private final ArchivedFileRepository files;
    private final RediscoverDao rediscover;
    private final TagService tags;
    private final UploadProperties upload;
    private final LifecycleProperties lifecycle;
    private final Clock clock;

    public DiscoveryService(
            ArchivedFileRepository files,
            RediscoverDao rediscover,
            TagService tags,
            UploadProperties upload,
            LifecycleProperties lifecycle,
            Clock clock) {
        this.files = files;
        this.rediscover = rediscover;
        this.tags = tags;
        this.upload = upload;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    public List<FileSummary> rediscover(AuthenticatedUser caller, String mode) {
        UUID owner = caller.id();
        List<UUID> ids =
                switch (mode) {
                    case "on-this-day" -> onThisDay(owner);
                    case "forgotten" ->
                            rediscover.forgotten(
                                    owner, clock.instant().minus(FORGOTTEN_AFTER), LIMIT);
                    case "random" -> rediscover.random(owner, 1);
                    default ->
                            throw new InvalidRequestException(
                                    "Unknown mode. Use on-this-day, forgotten or random.");
                };
        return summaries(owner, ids);
    }

    public Stats stats(AuthenticatedUser caller) {
        return new Stats(
                files.countByOwnerIdAndStatus(caller.id(), FileStatus.ARCHIVED),
                files.countByOwnerIdAndStatus(caller.id(), FileStatus.DISCARDED),
                files.sumSizeByOwnerId(caller.id()),
                upload.userQuota().toBytes(),
                lifecycle.retentionDays());
    }

    /** Leap-day files resurface on 28 February in years without a 29th. */
    private List<UUID> onThisDay(UUID owner) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        List<Integer> days =
                today.getMonthValue() == 2 && today.getDayOfMonth() == 28 && !today.isLeapYear()
                        ? List.of(28, 29)
                        : List.of(today.getDayOfMonth());
        return rediscover.onThisDay(owner, today.getMonthValue(), days, today.getYear(), LIMIT);
    }

    /** Loads rows and tags in two queries, keeping the order of {@code ids}. */
    private List<FileSummary> summaries(UUID owner, List<UUID> ids) {
        Map<UUID, ArchivedFile> byId =
                files.findAllByOwnerIdAndIdIn(owner, ids).stream()
                        .collect(Collectors.toMap(ArchivedFile::getId, Function.identity()));
        Map<UUID, List<TagRef>> tagsByFile = tags.tagsForFiles(owner, ids);
        return ids.stream()
                .map(byId::get)
                .filter(f -> f != null)
                .map(f -> FileMapper.toSummary(f, tagsByFile.getOrDefault(f.getId(), List.of())))
                .toList();
    }
}
