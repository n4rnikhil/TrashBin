package app.trashbin.archive.domain;

import java.util.Set;

/** Coarse file category derived from the detected media type; drives filters and icons. */
public enum TypeGroup {
    IMAGE,
    DOCUMENT,
    SPREADSHEET,
    PRESENTATION,
    TEXT,
    AUDIO,
    VIDEO,
    ARCHIVE,
    OTHER;

    private static final Set<String> DOCUMENTS =
            Set.of(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.oasis.opendocument.text",
                    "application/rtf",
                    "application/epub+zip");
    private static final Set<String> SPREADSHEETS =
            Set.of(
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.oasis.opendocument.spreadsheet",
                    "text/csv");
    private static final Set<String> PRESENTATIONS =
            Set.of(
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.oasis.opendocument.presentation");
    private static final Set<String> ARCHIVES =
            Set.of(
                    "application/zip",
                    "application/x-7z-compressed",
                    "application/x-rar-compressed",
                    "application/vnd.rar",
                    "application/gzip",
                    "application/x-gzip",
                    "application/x-tar",
                    "application/x-bzip2",
                    "application/x-xz");

    public static TypeGroup of(String mediaType) {
        if (DOCUMENTS.contains(mediaType)) {
            return DOCUMENT;
        }
        if (SPREADSHEETS.contains(mediaType)) {
            return SPREADSHEET;
        }
        if (PRESENTATIONS.contains(mediaType)) {
            return PRESENTATION;
        }
        if (ARCHIVES.contains(mediaType)) {
            return ARCHIVE;
        }
        if (mediaType.startsWith("image/")) {
            return IMAGE;
        }
        if (mediaType.startsWith("video/")) {
            return VIDEO;
        }
        if (mediaType.startsWith("audio/")) {
            return AUDIO;
        }
        if (mediaType.startsWith("text/")) {
            return TEXT;
        }
        return OTHER;
    }
}
