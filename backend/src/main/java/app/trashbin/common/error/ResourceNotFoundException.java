package app.trashbin.common.error;

/** The resource does not exist or is not owned by the caller; the two are indistinguishable. */
public class ResourceNotFoundException extends TrashBinException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException() {
        super(ErrorCode.NOT_FOUND, "The requested resource was not found.");
    }
}
