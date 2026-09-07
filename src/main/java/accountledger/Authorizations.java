package accountledger;

import java.util.List;
import java.util.Objects;

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
        var request = new Request(eventId, day, new Request.Hold(accountId, authorizationId, amount));
        var entry = journal.append(request, () -> {
            long cutoff = journal.lastSequence();
            if (decisions(cutoff).stream().anyMatch(auth -> auth.accountId().equals(accountId) && auth.id().equals(authorizationId))) {
                throw new IllegalArgumentException("Authorization ID already used for account: " + authorizationId);
            }
            var remaining = available(accountId, day, cutoff).subtract(amount);
            var decision = new Authorization(accountId, authorizationId, amount,
                    remaining.amount().signum() >= 0 ? APPROVED : DECLINED);
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
        for (var auth : decisions(knownThroughSequence)) {
            if (auth.accountId().equals(accountId) && auth.status() == APPROVED) {
                balance = balance.subtract(auth.amount());
            }
        }
        return balance;
    }

    /** Returns approvals and declines in the given prefix, in recording order. */
    public List<Authorization> decisions(long knownThroughSequence) {
        return journal.entries(knownThroughSequence).stream()
                .flatMap(entry -> entry.authorizations().stream())
                .toList();
    }
}
