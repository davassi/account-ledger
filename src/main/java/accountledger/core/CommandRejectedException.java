package accountledger.core;

/** An expected domain rejection that the command processor can record and continue past. */
public final class CommandRejectedException extends IllegalArgumentException {
    public CommandRejectedException(String reason) {
        super(reason);
    }
}
