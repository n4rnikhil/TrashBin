package app.trashbin.archive.mapper;

import app.trashbin.archive.domain.ArchivedFile;
import app.trashbin.archive.dto.FileDetail;
import app.trashbin.archive.dto.FileSummary;

public final class FileMapper {

    private FileMapper() {}

    public static FileSummary toSummary(ArchivedFile f) {
        return new FileSummary(
                f.getId(),
                f.getDisplayName(),
                f.getMediaType(),
                f.getTypeGroup().name(),
                f.getSizeBytes(),
                f.getStatus().name(),
                f.isFavorite(),
                f.getArchivedAt(),
                f.getOriginDate());
    }

    public static FileDetail toDetail(ArchivedFile f) {
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
                f.getArchivedAt(),
                f.getLastAccessedAt(),
                f.getDiscardedAt(),
                f.getPurgeAfter(),
                f.getUpdatedAt(),
                f.getVersion());
    }
}
