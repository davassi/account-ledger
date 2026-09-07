package accountledger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * In-memory, append-only history of postings and authorization decisions,
 * grouped by event, for a fixed set of accounts.
 * Entries and exposed snapshots are immutable. Designed for single-threaded replay.
 */
public final class Journal {
    private final Map<String, Money> openingBalances;
    private final List<Entry> entries = new ArrayList<>();

    /**
     * Copies the accounts and their opening balances. Account IDs must be unique;
     * each account's currency is determined by its opening balance.
     */
    public Journal(Account... accounts) {
        openingBalances = Arrays.stream(accounts)
                .collect(Collectors.toUnmodifiableMap(Account::id, Account::openingBalance));
    }

    /**
     * Validates all postings before recording them together, so a rejected entry
     * leaves no partial movements behind. Empty entries are allowed.
     *
     * <p>An identical monetary entry returns the original without consuming a
     * sequence. Reusing an ID with different data or for another event kind fails.
     * Booking days may move backwards; sequences always follow arrival order.
     */
    public Entry append(String eventId, int bookedDay, List<Posting> postings) {
        return append(eventId, bookedDay, postings, List.of());
    }

    /** Records already-decided outcomes together with their postings. */
    Entry append(String eventId, int bookedDay, List<Posting> postings, List<Authorization> authorizations) {
        var entry = new Entry(lastSequence() + 1, eventId, bookedDay, postings, authorizations);

        for (final var existing : entries) {
            if (existing.eventId().equals(eventId)) {
                if (existing.bookedDay() != bookedDay || !existing.postings().equals(entry.postings())
                        || !existing.authorizations().equals(entry.authorizations())) {
                    throw new IllegalArgumentException("Conflicting event ID: " + eventId);
                }
                return existing;
            }
        }
        for (var posting : entry.postings()) {
            validateCurrency(posting.accountId(), posting.delta());
        }
        for (var authorization : entry.authorizations()) {
            validateCurrency(authorization.accountId(), authorization.amount());
        }
        entries.add(entry);
        return entry;
    }

    /**
     * Adds this account's eligible postings to its opening balance. Both the value
     * day and the knowledge cutoff are inclusive; the booked day is not a filter.
     *
     * <p>Sequence zero includes only the opening balance. A backdated posting
     * changes results only for queries whose cutoff includes its sequence,
     * preserving earlier views of the same value day.
     *
     * @param accountId an account registered at construction
     * @param valueDay latest value day to include; must be positive
     * @param knownThroughSequence latest entry to include, from zero to {@link #lastSequence()}
     */
    public Money balance(String accountId, int valueDay, long knownThroughSequence) {
        if (valueDay < 1 || knownThroughSequence < 0 || knownThroughSequence > lastSequence()) {
            throw new IllegalArgumentException("Invalid value day or knowledge cutoff");
        }
        var opening = openingBalance(accountId);
        return entries.stream()
                .filter(entry -> entry.sequence() <= knownThroughSequence)
                .flatMap(entry -> entry.postings().stream())
                .filter(posting -> posting.accountId().equals(accountId) && posting.valueDay() <= valueDay)
                .map(Posting::delta)
                .reduce(opening, Money::add);
    }

    /**
     * Returns the last recorded sequence, or zero before the first entry.
     */
    public long lastSequence() {
        return entries.size();
    }

    /**
     * Returns an immutable snapshot. Later appends do not change the returned list.
     */
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    private void validateCurrency(String accountId, Money amount) {
        if (openingBalance(accountId).currency() != amount.currency()) {
            throw new IllegalArgumentException("Currency mismatch for account: " + accountId);
        }
    }

    private Money openingBalance(String accountId) {
        var opening = openingBalances.get(accountId);
        if (opening == null) {
            throw new IllegalArgumentException("Unknown account: " + accountId);
        }
        return opening;
    }

    /**
     * Immutable record of one event's postings and decisions, all visible together
     * at one sequence. E10 has three postings; E3 has a decision and no postings.
     *
     * @param sequence journal-assigned position starting at one, independent of event IDs and dates
     * @param eventId identity used to detect duplicate or conflicting submissions
     * @param bookedDay positive booking day declared by the event
     * @param postings ordered, defensively copied movements, each with its own value day
     * @param authorizations ordered, defensively copied decisions made when processing the event
     */
    public record Entry(long sequence, String eventId, int bookedDay, List<Posting> postings,
                        List<Authorization> authorizations) {
        public Entry {
            Objects.requireNonNull(eventId, "eventId");
            if (eventId.isBlank()) {
                throw new IllegalArgumentException("Event ID must not be blank");
            }
            if (sequence < 1 || bookedDay < 1) {
                throw new IllegalArgumentException("Sequence and booked day must be positive");
            }
            postings = List.copyOf(postings);
            authorizations = List.copyOf(authorizations);
        }
    }
}
