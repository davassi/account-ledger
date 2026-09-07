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
        for (var entry : journal.entries()) {
            if (entry.eventId().equals(eventId)) {
                if (entry.bookedDay() == day && entry.postings().isEmpty() && entry.authorizations().size() == 1) {
                    var original = entry.authorizations().getFirst();
                    if (original.accountId().equals(accountId) && original.id().equals(authorizationId)
                            && original.amount().equals(amount)) {
                        return original;
                    }
                }
                throw new IllegalArgumentException("Conflicting event ID: " + eventId);
            }
        }
        if (decisions().stream().anyMatch(auth -> auth.accountId().equals(accountId) && auth.id().equals(authorizationId))) {
            throw new IllegalArgumentException("Authorization ID already used for account: " + authorizationId);
        }
        var remaining = available(accountId, day).subtract(amount);
        var decision = new Authorization(accountId, authorizationId, amount,
                remaining.amount().signum() >= 0 ? APPROVED : DECLINED);
        journal.append(eventId, day, List.of(), List.of(decision));
        return decision;
    }

    /**
     * Returns the current ledger balance minus all approved holds. The day is
     * the runner's current day, used to exclude future-value postings. Holds
     * take effect when recorded and have no implicit expiry.
     */
    public Money available(String accountId, int day) {
        var balance = journal.balance(accountId, day, journal.lastSequence());
        for (var auth : decisions()) {
            if (auth.accountId().equals(accountId) && auth.status() == APPROVED) {
                balance = balance.subtract(auth.amount());
            }
        }
        return balance;
    }

    /** Returns an immutable snapshot of approvals and declines in recording order. */
    public List<Authorization> decisions() {
        return journal.entries().stream()
                .flatMap(entry -> entry.authorizations().stream())
                .toList();
    }
}
