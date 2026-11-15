package app.trashbin.common.error;

/** The uploaded content exceeded the per-file size limit (counted on the stream, not headers). */
public class FileTooLargeException extends TrashBinException {

    private static final long serialVersionUID = 1L;

    public FileTooLargeException(long maxBytes) {
        super(
                ErrorCode.FILE_TOO_LARGE,
                "The file exceeds the maximum size of " + (maxBytes / (1024 * 1024)) + " MB.");
    }
}
