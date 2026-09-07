package accountledger;

import java.util.List;
import java.util.Objects;

import static accountledger.Authorization.Status.DECLINED;
import static accountledger.Authorization.Status.SETTLED;
import static accountledger.Diagnostic.DECLINED_AUTHORIZATION;
import static accountledger.Diagnostic.MISSING_AUTHORIZATION;

/** Posts final settlements and releases approved holds through new journal entries. */
public final class Settlements {
    private final Journal journal;
    private final Authorizations authorizations;

    public Settlements(Journal journal) {
        this.journal = Objects.requireNonNull(journal, "journal");
        this.authorizations = new Authorizations(journal);
    }

    /**
     * Debits the positive amount at its value day and releases the entire approved
     * hold at the recording sequence. Available funds do not limit a settlement.
     *
     * <p>A missing or declined authorization still produces a debit, with a
     * diagnostic. One final settlement is allowed per account and authorization
     * reference; identical event retries return their original entry.
     */
    public Journal.Entry settle(String eventId, int bookedDay, String accountId, String authorizationId,
                                Money amount, int valueDay) {
        var data = new Request.Settlement(accountId, authorizationId, amount, valueDay);
        var request = new Request(eventId, bookedDay, data);
        return journal.append(request, () -> {
            Objects.requireNonNull(authorizationId, "authorizationId");
            Objects.requireNonNull(amount, "amount");
            if (authorizationId.isBlank() || amount.signum() <= 0) {
                throw new IllegalArgumentException("Settlement requires an authorization ID and a positive amount");
            }
            var postings = List.of(new Posting(accountId, valueDay, amount.negate()));
            long cutoff = journal.lastSequence();
            if (authorizations.wasSettled(accountId, authorizationId, cutoff)) {
                throw new IllegalArgumentException("Authorization reference already settled: " + authorizationId);
            }

            var original = authorizations.find(accountId, authorizationId, cutoff);
            if (original.isEmpty()) {
                return new Journal.Effects(postings, List.of(), List.of(MISSING_AUTHORIZATION));
            }
            var hold = original.get();
            if (hold.status() == DECLINED) {
                return new Journal.Effects(postings, List.of(), List.of(DECLINED_AUTHORIZATION));
            }

            var settled = new Authorization(accountId, authorizationId, hold.amount(), SETTLED);
            return new Journal.Effects(postings, List.of(settled));
        });
    }
}
