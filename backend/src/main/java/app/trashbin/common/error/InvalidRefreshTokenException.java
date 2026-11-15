package app.trashbin.common.error;

/** The refresh token is missing, unknown, expired, revoked or was already used. */
public class InvalidRefreshTokenException extends TrashBinException {

    private static final long serialVersionUID = 1L;

    public InvalidRefreshTokenException() {
        super(ErrorCode.INVALID_REFRESH_TOKEN, "Your session has expired. Please sign in again.");
    }
}
