package app.trashbin.archive.mapper;

import app.trashbin.archive.domain.ArchivedFile;
import app.trashbin.archive.dto.FileDetail;
import app.trashbin.archive.dto.FileSummary;
import app.trashbin.tag.dto.TagDtos.TagRef;
import java.util.List;

public final class FileMapper {

    private FileMapper() {}

    public static FileSummary toSummary(ArchivedFile f, List<TagRef> tags) {
        return new FileSummary(
                f.getId(),
                f.getDisplayName(),
                f.getMediaType(),
                f.getTypeGroup().name(),
                f.getSizeBytes(),
                f.getStatus().name(),
                f.isFavorite(),
                f.getArchivedAt(),
                f.getOriginDate(),
                List.copyOf(tags));
    }

    public static FileDetail toDetail(ArchivedFile f, List<TagRef> tags) {
        return new FileDetail(
                f.getId(),
                f.getDisplayName(),
                f.getOriginalFilename(),
                f.getMediaType(),
                f.getTypeGroup().name(),
                f.getSizeBytes(),
                f.getSha256(),
                f.getStatus().name(),
                f.isFavorite(),
                f.getNotes(),
                f.getContextNote(),
                f.getOriginDate(),
                List.copyOf(tags),
                f.getArchivedAt(),
                f.getLastAccessedAt(),
                f.getDiscardedAt(),
                f.getPurgeAfter(),
                f.getUpdatedAt(),
                f.getVersion());
    }
}
