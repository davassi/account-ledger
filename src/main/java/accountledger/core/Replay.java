package accountledger.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Executes requests and keeps an append-only history of rejected attempts. */
public final class Replay {
    private final Journal journal;
    private final List<Rejection> rejections = new ArrayList<>();

    public Replay(Journal journal) {
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    /** Returns false for a recorded rejection; declines and accepted anomalies return true. */
    public boolean process(int processingDay, Request request) {
        Objects.requireNonNull(request, "request");
        if (processingDay < 1) {
            throw new IllegalArgumentException("Processing day must be positive");
        }
        try {
            dispatch(request);
            return true;
        } catch (RequestRejectedException rejected) {
            rejections.add(new Rejection(processingDay, request, rejected.getMessage()));
            return false;
        }
    }

    /** Returns an immutable snapshot; later attempts cannot change an earlier report. */
    public List<Rejection> rejections() {
        return List.copyOf(rejections);
    }

    private void dispatch(Request request) {
        var id = request.eventId();
        int day = request.bookedDay();
        switch (request.data()) {
            case Request.Postings data -> journal.append(id, day, data.postings());
            case Request.Hold data -> new Authorizations(journal)
                    .authorize(id, day, data.accountId(), data.authorizationId(), data.amount());
            case Request.Settlement data -> new Settlements(journal)
                    .settle(id, day, data.accountId(), data.authorizationId(), data.amount(), data.valueDay());
            case Request.Reversal data -> new Reversals(journal)
                    .reverse(id, day, data.accountId(), data.originalEventId(), data.valueDay());
            case Request.DayClose data -> new DailyClosing(journal).close(id, day, data.accountId());
            case Request.FeeReassessment data -> new DailyClosing(journal).reassess(id, day, data.accountId());
            case Request.InterestCapitalization data -> new Interest(journal).capitalize(id, day, data.accountId());
        }
    }
}
