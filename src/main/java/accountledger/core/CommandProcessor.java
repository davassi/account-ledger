package accountledger.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Executes commands and keeps an append-only history of rejected attempts. */
public final class CommandProcessor {
    private final Journal journal;
    private final List<Rejection> rejections = new ArrayList<>();

    public CommandProcessor(Journal journal) {
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    /** Returns false for a recorded rejection; declines and accepted anomalies return true. */
    public boolean process(int processingDay, Command command) {
        Objects.requireNonNull(command, "command");
        if (processingDay < 1) {
            throw new IllegalArgumentException("Processing day must be positive");
        }
        try {
            dispatch(command);
            return true;
        } catch (CommandRejectedException rejected) {
            rejections.add(new Rejection(processingDay, command, rejected.getMessage()));
            return false;
        }
    }

    /** Returns an immutable snapshot; later attempts cannot change an earlier report. */
    public List<Rejection> rejections() {
        return List.copyOf(rejections);
    }

    private void dispatch(Command command) {
        var id = command.eventId();
        int day = command.bookedDay();
        switch (command.data()) {
            case Command.Postings data -> journal.append(id, day, data.postings());
            case Command.Hold data -> new Authorizations(journal)
                    .authorize(id, day, data.accountId(), data.authorizationId(), data.amount());
            case Command.Settlement data -> new Settlements(journal)
                    .settle(id, day, data.accountId(), data.authorizationId(), data.amount(), data.valueDay());
            case Command.Reversal data -> new Reversals(journal)
                    .reverse(id, day, data.accountId(), data.originalEventId(), data.valueDay());
            case Command.DayClose data -> new DailyClosing(journal).close(id, day, data.accountId());
            case Command.FeeReassessment data -> new DailyClosing(journal).reassess(id, day, data.accountId());
            case Command.InterestCapitalization data -> new Interest(journal).capitalize(id, day, data.accountId());
        }
    }
}
