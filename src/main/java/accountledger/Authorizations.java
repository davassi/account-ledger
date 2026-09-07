package accountledger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static accountledger.Authorization.Status.APPROVED;
import static accountledger.Authorization.Status.DECLINED;

/**
 * Decides holds using current knowledge and reads the recorded outcomes.
 * No balances or decisions are cached outside the journal.
 */
public final class Authorizations {
    private final Journal journal;

    public Authorizations(Journal journal) {
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    /**
     * Records an approval if the remaining available balance is non-negative,
     * otherwise a decline. The caller supplies the current processing day.
     *
     * <p>An identical event retry returns its recorded decision, even if funds
     * have changed. An authorization ID can be used only once per account.
     */
    public Authorization authorize(String eventId, int day, String accountId, String authorizationId, Money amount) {
        var hold = new Request.Hold(accountId, authorizationId, amount);
        var request = new Request(eventId, day, hold);
        var entry = journal.append(request, () -> {
            var decision = decide(hold, day, journal.lastSequence());
            return new Journal.Effects(List.of(), List.of(decision));
        });
        return entry.authorizations().getFirst();
    }

    /**
     * Subtracts holds known at the cutoff from the ledger balance at that same
     * cutoff. The value day filters monetary postings; holds take effect at their
     * recording sequence. To reproduce an original daily close, use its saved
     * sequence. Later entries cannot change that view or its recorded decisions.
     */
    public Money available(String accountId, int valueDay, long knownThroughSequence) {
        var balance = journal.balance(accountId, valueDay, knownThroughSequence);
        var heldAmount = states(knownThroughSequence).stream()
                .filter(auth -> auth.accountId().equals(accountId) && auth.status() == APPROVED)
                .map(Authorization::amount)
                .reduce(Money.of(balance.currency(), "0"), Money::add);
        return balance.subtract(heldAmount);
    }

    /** Returns all recorded authorization states, including settlement, in recording order. */
    public List<Authorization> decisions(long knownThroughSequence) {
        return journal.entries(knownThroughSequence).stream()
                .flatMap(entry -> entry.authorizations().stream())
                .toList();
    }

    /** Returns the latest state per account and authorization ID, in first-seen order. */
    public List<Authorization> states(long knownThroughSequence) {
        var latest = new LinkedHashMap<Key, Authorization>();
        decisions(knownThroughSequence).forEach(auth ->
                latest.put(new Key(auth.accountId(), auth.id()), auth));
        return List.copyOf(latest.values());
    }

    Optional<Authorization> find(String accountId, String authorizationId, long cutoff) {
        return states(cutoff).stream()
                .filter(auth -> auth.accountId().equals(accountId) && auth.id().equals(authorizationId))
                .findFirst();
    }

    /** Recognizes captures even when the recorded authorization remains declined. */
    boolean wasSettled(String accountId, String authorizationId, long cutoff) {
        return journal.entries(cutoff).stream()
                .map(entry -> entry.request().data())
                .anyMatch(data -> data instanceof Request.Settlement settlement
                        && settlement.accountId().equals(accountId)
                        && settlement.authorizationId().equals(authorizationId));
    }

    private Authorization decide(Request.Hold hold, int day, long cutoff) {
        var accountId = hold.accountId();
        var authorizationId = hold.authorizationId();
        if (find(accountId, authorizationId, cutoff).isPresent()) {
            throw new RequestRejectedException("Authorization ID already used for account: " + authorizationId);
        }
        journal.validateCurrency(accountId, hold.amount());
        var remaining = available(accountId, day, cutoff).subtract(hold.amount());
        var status = remaining.signum() >= 0 ? APPROVED : DECLINED;
        return new Authorization(accountId, authorizationId, hold.amount(), status);
    }

    private record Key(String accountId, String authorizationId) {}
}
