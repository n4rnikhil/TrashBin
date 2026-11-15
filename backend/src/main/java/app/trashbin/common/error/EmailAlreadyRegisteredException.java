package app.trashbin.common.error;

/** Registration conflict. An accepted enumeration risk (docs/SECURITY_GUIDELINES.md section 11). */
public class EmailAlreadyRegisteredException extends TrashBinException {

    private static final long serialVersionUID = 1L;

    public EmailAlreadyRegisteredException() {
        super(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists.");
    }
}
