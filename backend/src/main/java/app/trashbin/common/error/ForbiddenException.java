package app.trashbin.common.error;

/** The request is understood but refused (for example a failed origin check). */
public class ForbiddenException extends TrashBinException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException() {
        super(ErrorCode.FORBIDDEN, "You are not allowed to do this.");
    }
}
