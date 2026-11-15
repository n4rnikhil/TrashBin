package app.trashbin.common.error;

/**
 * Login failed. Deliberately identical for unknown email, wrong password, locked and disabled
 * accounts so the response never reveals which one it was.
 */
public class InvalidCredentialsException extends TrashBinException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS, "The email or password is incorrect.");
    }
}
