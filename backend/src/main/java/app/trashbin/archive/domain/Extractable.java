package app.trashbin.archive.domain;

import java.util.Set;

/** Types whose text is extracted for content search (M4b). Everything else is SKIPPED. */
public final class Extractable {

    private static final Set<String> TYPES =
            Set.of(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.oasis.opendocument.text",
                    "application/vnd.oasis.opendocument.spreadsheet",
                    "application/vnd.oasis.opendocument.presentation",
                    "application/rtf",
                    "text/plain",
                    "text/csv",
                    "text/markdown",
                    "text/x-web-markdown");

    private Extractable() {}

    public static boolean isExtractable(String mediaType) {
        return TYPES.contains(mediaType);
    }
}
