package accountledger;

import java.util.List;
import java.util.Objects;

/** Compensates an original monetary event through a new journal entry. */
public final class Reversals {
    private final Journal journal;

    public Reversals(Journal journal) {
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    /**
     * Reverses all postings of one account's event at the requested value day.
     * Holds, diagnostics and separate fee entries are left as recorded.
     * Only one full reversal is allowed; identical event retries return that entry.
     */
    public Journal.Entry reverse(String eventId, int bookedDay, String accountId, String originalEventId, int valueDay) {
        var data = new Request.Reversal(accountId, originalEventId, valueDay);
        var request = new Request(eventId, bookedDay, data);
        return journal.append(request, () -> {
            var original = findReversibleEvent(accountId, originalEventId, journal.lastSequence());
            var compensations = original.postings().stream()
                    .map(posting -> new Posting(accountId, valueDay, posting.delta().negate()))
                    .toList();
            return new Journal.Effects(compensations, List.of());
        });
    }

    private Journal.Entry findReversibleEvent(String accountId, String originalEventId, long cutoff) {
        var original = journal.find(originalEventId, cutoff)
                .orElseThrow(() -> new RequestRejectedException("Unknown original event: " + originalEventId));
        if (original.postings().isEmpty() || original.request().data() instanceof Request.Reversal) {
            throw new RequestRejectedException("Only original monetary events can be reversed");
        }
        if (original.postings().stream().anyMatch(posting -> !posting.accountId().equals(accountId))) {
            throw new RequestRejectedException("All original postings must belong to account: " + accountId);
        }

        boolean alreadyReversed = journal.entries(cutoff).stream()
                .map(entry -> entry.request().data())
                .anyMatch(input -> input instanceof Request.Reversal reversal
                        && reversal.originalEventId().equals(originalEventId));
        if (alreadyReversed) {
            throw new RequestRejectedException("Event already reversed: " + originalEventId);
        }
        return original;
    }
}
