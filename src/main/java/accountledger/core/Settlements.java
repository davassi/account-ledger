package accountledger.core;

import accountledger.domain.Authorization;
import accountledger.domain.Command;
import accountledger.domain.CommandRejectedException;
import accountledger.domain.Diagnostic;
import accountledger.domain.Money;
import accountledger.domain.Posting;

import java.util.List;
import java.util.Objects;

import static accountledger.domain.Authorization.Status.DECLINED;
import static accountledger.domain.Authorization.Status.SETTLED;
import static accountledger.domain.Diagnostic.DECLINED_AUTHORIZATION;

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
     * <p>A missing authorization is rejected without changing the journal. A
     * declined but recorded authorization is recorded with a diagnostic and moves
     * no money: a decline is final, so no capture against it can be honoured.
     * One settlement is recorded per account and authorization reference;
     * identical event retries return their original entry.
     */
    public Journal.Entry settle(String eventId, int bookedDay, String accountId, String authorizationId,
                                Money amount, int valueDay) {
        var settlement = new Command.Settlement(accountId, authorizationId, amount, valueDay);
        var command = new Command(eventId, bookedDay, settlement);
        return journal.append(command, () -> {
            long cutoff = journal.lastSequence();
            validate(settlement, cutoff);
            var authorization = authorizations.find(accountId, authorizationId, cutoff)
                    .orElseThrow(() -> new CommandRejectedException("Unknown authorization: " + authorizationId));
            if (authorization.status() == DECLINED) {
                // The attempt becomes an auditable fact; refusing the decline keeps the money still.
                return new Journal.Effects(List.of(), List.of(), List.of(DECLINED_AUTHORIZATION));
            }

            var debit = new Posting(accountId, valueDay, amount.negate());
            var settled = new Authorization(accountId, authorizationId, authorization.amount(), SETTLED);
            return new Journal.Effects(List.of(debit), List.of(settled));
        });
    }

    /** Rejects malformed input and a reference this account has already settled or refused. */
    private void validate(Command.Settlement settlement, long cutoff) {
        var authorizationId = Objects.requireNonNull(settlement.authorizationId(), "authorizationId");
        var amount = Objects.requireNonNull(settlement.amount(), "amount");
        if (authorizationId.isBlank() || amount.signum() <= 0) {
            throw new CommandRejectedException("Settlement requires an authorization ID and a positive amount");
        }
        if (authorizations.wasSettled(settlement.accountId(), authorizationId, cutoff)) {
            throw new CommandRejectedException("Settlement already recorded for authorization: " + authorizationId);
        }
    }
}
