package accountledger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * In-memory, append-only history of postings, authorization states, diagnostics and accruals,
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
        var data = new Request.Postings(postings);
        return append(new Request(eventId, bookedDay, data),
                () -> new Effects(data.postings(), List.of()));
    }

    /**
     * Returns a recorded outcome for an identical request, or rejects a conflicting
     * ID. Only a new request evaluates the supplier, which must compute effects
     * without modifying this journal. Failed computation or validation appends nothing.
     */
    Entry append(Request request, Supplier<Effects> decide) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(decide, "decide");
        var duplicate = find(request.eventId(), lastSequence());
        if (duplicate.isPresent()) {
            var existing = duplicate.get();
            if (!existing.request().equals(request)) {
                throw new IllegalArgumentException("Conflicting event ID: " + request.eventId());
            }
            return existing;
        }

        var entry = new Entry(lastSequence() + 1, request, decide.get());
        entry.postings().forEach(posting -> validateCurrency(posting.accountId(), posting.delta()));
        entry.authorizations().forEach(auth -> validateCurrency(auth.accountId(), auth.amount()));
        entry.accruals().forEach(accrual -> validateCurrency(accrual.accountId(), accrual.amount()));
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
        if (valueDay < 1) {
            throw new IllegalArgumentException("Value day must be positive");
        }
        var opening = openingBalance(accountId);
        var postedAmount = entries(knownThroughSequence).stream()
                .flatMap(entry -> entry.postings().stream())
                .filter(posting -> posting.accountId().equals(accountId) && posting.valueDay() <= valueDay)
                .map(Posting::delta)
                .reduce(Money.of(opening.currency(), "0"), Money::add);
        return opening.add(postedAmount);
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
        return entries(lastSequence());
    }

    /** Returns an immutable prefix, including the cutoff entry; zero is empty. */
    public List<Entry> entries(long knownThroughSequence) {
        if (knownThroughSequence < 0 || knownThroughSequence > lastSequence()) {
            throw new IllegalArgumentException("Invalid knowledge cutoff");
        }
        return List.copyOf(entries.subList(0, Math.toIntExact(knownThroughSequence)));
    }

    /** Finds an event only if it is already known at the given cutoff. */
    public Optional<Entry> find(String eventId, long knownThroughSequence) {
        return entries(knownThroughSequence).stream()
                .filter(entry -> entry.eventId().equals(eventId))
                .findFirst();
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
     * Original request and its immutable effects, visible together at one sequence.
     * Keeping the input allows retries to be recognized without recalculating outcomes.
     *
     * @param sequence journal-assigned position starting at one, independent of event IDs and dates
     * @param request original event ID, booked day and input data
     * @param effects postings and decisions calculated on the first submission
     */
    public record Entry(long sequence, Request request, Effects effects) {
        public Entry {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(effects, "effects");
            if (sequence < 1) {
                throw new IllegalArgumentException("Sequence must be positive");
            }
        }

        public String eventId() { return request.eventId(); }
        public int bookedDay() { return request.bookedDay(); }
        public List<Posting> postings() { return effects.postings(); }
        public List<Authorization> authorizations() { return effects.authorizations(); }
        public List<Diagnostic> diagnostics() { return effects.diagnostics(); }
        public List<Accrual> accruals() { return effects.accruals(); }
    }

    /** Monetary effects and non-spendable facts become visible together in a single entry. */
    public record Effects(List<Posting> postings, List<Authorization> authorizations,
                          List<Diagnostic> diagnostics, List<Accrual> accruals) {
        public Effects(List<Posting> postings, List<Authorization> authorizations) {
            this(postings, authorizations, List.of());
        }

        public Effects(List<Posting> postings, List<Authorization> authorizations, List<Diagnostic> diagnostics) {
            this(postings, authorizations, diagnostics, List.of());
        }

        public Effects {
            postings = List.copyOf(postings);
            authorizations = List.copyOf(authorizations);
            diagnostics = List.copyOf(diagnostics);
            accruals = List.copyOf(accruals);
        }
    }
}
