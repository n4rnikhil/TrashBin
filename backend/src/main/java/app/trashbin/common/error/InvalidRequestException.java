package app.trashbin.common.error;

/** A request that passed bean validation but violates a business-level input rule. */
public class InvalidRequestException extends TrashBinException {

    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String safeMessage) {
        super(ErrorCode.VALIDATION_FAILED, safeMessage);
    }
}
