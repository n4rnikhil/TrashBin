package app.trashbin.common.error;

/** The requested lifecycle step does not apply to the item's current state. */
public class InvalidStateTransitionException extends TrashBinException {

    private static final long serialVersionUID = 1L;

    public InvalidStateTransitionException(String safeMessage) {
        super(ErrorCode.INVALID_STATE_TRANSITION, safeMessage);
    }
}
