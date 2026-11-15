package app.trashbin.common.error;

/** An edit was sent without the If-Match header that protects against lost updates. */
public class PreconditionRequiredException extends TrashBinException {

    private static final long serialVersionUID = 1L;

    public PreconditionRequiredException() {
        super(
                ErrorCode.PRECONDITION_REQUIRED,
                "Send the If-Match header with the version you are editing.");
    }
}
