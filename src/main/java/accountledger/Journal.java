package accountledger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class Journal {
    private final Map<String, Money> openingBalances;
    private final List<Batch> batches = new ArrayList<>();

    public Journal(Account... accounts) {
        openingBalances = Arrays.stream(accounts)
                .collect(Collectors.toUnmodifiableMap(Account::id, Account::openingBalance));
    }

    public Batch append(String eventId, int bookedDay, List<Posting> postings) {
        var batch = new Batch(lastSequence() + 1, eventId, bookedDay, postings);
        for (var existing : batches) {
            if (existing.eventId().equals(eventId)) {
                if (existing.bookedDay() != bookedDay || !existing.postings().equals(batch.postings())) {
                    throw new IllegalArgumentException("Conflicting event ID: " + eventId);
                }
                return existing;
            }
        }
        for (var posting : batch.postings()) {
            if (openingBalance(posting.accountId()).currency() != posting.delta().currency()) {
                throw new IllegalArgumentException("Currency mismatch for account: " + posting.accountId());
            }
        }
        batches.add(batch);
        return batch;
    }

    public Money balance(String accountId, int valueDay, long knownThroughSequence) {
        if (valueDay < 1 || knownThroughSequence < 0 || knownThroughSequence > lastSequence()) {
            throw new IllegalArgumentException("Invalid value day or knowledge cutoff");
        }
        var opening = openingBalance(accountId);
        return batches.stream()
                .filter(batch -> batch.sequence() <= knownThroughSequence)
                .flatMap(batch -> batch.postings().stream())
                .filter(posting -> posting.accountId().equals(accountId) && posting.valueDay() <= valueDay)
                .map(Posting::delta)
                .reduce(opening, Money::add);
    }

    public long lastSequence() {
        return batches.size();
    }

    public List<Batch> batches() {
        return List.copyOf(batches);
    }

    private Money openingBalance(String accountId) {
        var opening = openingBalances.get(accountId);
        if (opening == null) {
            throw new IllegalArgumentException("Unknown account: " + accountId);
        }
        return opening;
    }

    public record Batch(long sequence, String eventId, int bookedDay, List<Posting> postings) {
        public Batch {
            Objects.requireNonNull(eventId, "eventId");
            if (eventId.isBlank()) {
                throw new IllegalArgumentException("Event ID must not be blank");
            }
            if (sequence < 1 || bookedDay < 1) {
                throw new IllegalArgumentException("Sequence and booked day must be positive");
            }
            postings = List.copyOf(postings);
        }
    }
}
