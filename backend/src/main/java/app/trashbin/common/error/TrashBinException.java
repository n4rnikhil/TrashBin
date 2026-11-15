package app.trashbin.common.error;

/**
 * Base class for expected, contract-level failures. The message is safe to show to API clients:
 * never put secrets, identifiers of other users, or internal details in it.
 */
public abstract class TrashBinException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    protected TrashBinException(ErrorCode code, String safeMessage) {
        super(safeMessage);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
