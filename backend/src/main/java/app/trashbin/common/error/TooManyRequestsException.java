package app.trashbin.common.error;

/** The caller exceeded a rate limit and should retry after the given delay. */
public class TooManyRequestsException extends TrashBinException {

    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_REQUESTS, "Too many requests. Please try again shortly.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
