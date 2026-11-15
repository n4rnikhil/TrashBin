package app.trashbin.common.error;

/** The client edited an outdated version (If-Match did not match the current version). */
public class VersionConflictException extends TrashBinException {

    private static final long serialVersionUID = 1L;

    public VersionConflictException() {
        super(
                ErrorCode.VERSION_CONFLICT,
                "This item was changed in the meantime. Reload and try again.");
    }
}
