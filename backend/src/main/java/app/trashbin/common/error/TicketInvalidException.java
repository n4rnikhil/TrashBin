package app.trashbin.common.error;

/** A download/preview link that is malformed, tampered with or expired. */
public class TicketInvalidException extends TrashBinException {

    private static final long serialVersionUID = 1L;

    public TicketInvalidException() {
        super(
                ErrorCode.TICKET_INVALID,
                "This link has expired. Open the file again to get a new one.");
    }
}
