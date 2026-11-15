package app.trashbin.archive.service;

import app.trashbin.archive.dto.FileSummary;
import app.trashbin.common.error.ErrorCode;
import app.trashbin.common.error.TrashBinException;
import java.util.Map;

/** Expected upload rejections. Messages are safe to show to the uploader. */
public final class UploadExceptions {

    private UploadExceptions() {}

    /** The caller already archived identical bytes; {@code existing} identifies that file. */
    public static class DuplicateFileException extends TrashBinException {

        private static final long serialVersionUID = 1L;

        private final transient FileSummary existing;

        public DuplicateFileException(FileSummary existing) {
            super(ErrorCode.DUPLICATE_FILE, "You have already archived this file.");
            this.existing = existing;
        }

        @Override
        public Map<String, Object> properties() {
            return Map.of("existing", existing);
        }
    }

    public static class QuotaExceededException extends TrashBinException {

        private static final long serialVersionUID = 1L;

        public QuotaExceededException() {
            super(ErrorCode.QUOTA_EXCEEDED, "This upload would exceed your storage quota.");
        }
    }

    /** Refused by policy (extension denylist or the upload scanner). */
    public static class UnsupportedFileTypeException extends TrashBinException {

        private static final long serialVersionUID = 1L;

        public UnsupportedFileTypeException() {
            super(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "This type of file cannot be archived.");
        }
    }

    public static class FileSignatureMismatchException extends TrashBinException {

        private static final long serialVersionUID = 1L;

        public FileSignatureMismatchException() {
            super(
                    ErrorCode.FILE_SIGNATURE_MISMATCH,
                    "The file's content does not match its extension.");
        }
    }
}
