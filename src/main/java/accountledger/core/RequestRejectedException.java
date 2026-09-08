package accountledger.core;

/** An expected domain rejection that the replay can record and continue past. */
public final class RequestRejectedException extends IllegalArgumentException {
    public RequestRejectedException(String reason) {
        super(reason);
    }
}
