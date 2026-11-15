package app.trashbin.common.error;

/** Self-registration is turned off by configuration. */
public class RegistrationDisabledException extends TrashBinException {

    private static final long serialVersionUID = 1L;

    public RegistrationDisabledException() {
        super(ErrorCode.REGISTRATION_DISABLED, "Registration is currently disabled.");
    }
}
