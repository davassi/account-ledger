package accountledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

/** Calculates daily interest; only capitalization adds spendable funds. */
public final class Interest {

    private static final BigDecimal DAILY_RATE = new BigDecimal("0.0004");

    private static final int FINAL_DAY = 6;
    private final Journal journal;

    public Interest(Journal journal) {
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    /** Returns the latest set of accruals for closed days known at this cutoff. */
    public List<Accrual> accruals(String accountId, long knownThroughSequence) {
        return latestAccrualEntry(accountId, knownThroughSequence)
                .map(Journal.Entry::accruals).orElse(List.of());
    }

    /** Credits exactly the rounded daily sum, once, after Day 6 has been closed. */
    public Journal.Entry capitalize(String eventId, String accountId) {
        var request = new Request(eventId, FINAL_DAY, new Request.InterestCapitalization(accountId));
        return journal.append(request, () -> {
            var daily = accrualsToCapitalize(accountId, journal.lastSequence());
            var total = daily.stream().map(Accrual::amount)
                    .reduce(Money.of(daily.getFirst().amount().currency(), "0"), Money::add);
            return new Journal.Effects(List.of(new Posting(accountId, FINAL_DAY, total)), List.of());
        });
    }

    /** Called by DailyClosing before append, so newly calculated fees enter the interest basis. */
    List<Accrual> calculate(String accountId, int throughDay, long cutoff, List<Posting> pendingFees) {
        requireUncapitalized(accountId, cutoff);
        if (throughDay < 1 || throughDay > FINAL_DAY) {
            throw new IllegalArgumentException("Interest supports Day 1 through Day 6");
        }
        return IntStream.rangeClosed(1, throughDay).mapToObj(day -> {
            var balance = journal.balance(accountId, day, cutoff);
            var zero = Money.of(balance.currency(), "0");
            var fees = pendingFees.stream().filter(fee -> fee.valueDay() <= day)
                    .map(Posting::delta).reduce(zero, Money::add);
            var afterFees = balance.add(fees);
            var amount = afterFees.signum() > 0 ? afterFees.multiply(DAILY_RATE, RoundingMode.HALF_EVEN) : zero;
            return new Accrual(accountId, day, amount);
        }).toList();
    }

    private List<Accrual> accrualsToCapitalize(String accountId, long cutoff) {
        requireUncapitalized(accountId, cutoff);
        var latest = latestAccrualEntry(accountId, cutoff)
                .orElseThrow(() -> new IllegalArgumentException("Close all six days before capitalization"));
        if (latest.accruals().size() != FINAL_DAY) {
            throw new IllegalArgumentException("Close all six days before capitalization");
        }
        boolean changedSinceClose = journal.entries(cutoff).stream()
                .filter(entry -> entry.sequence() > latest.sequence())
                .flatMap(entry -> entry.postings().stream())
                .anyMatch(posting -> posting.accountId().equals(accountId) && posting.valueDay() <= FINAL_DAY);
        if (changedSinceClose) {
            throw new IllegalArgumentException("Reassess changed balances before capitalization");
        }
        return latest.accruals();
    }

    private Optional<Journal.Entry> latestAccrualEntry(String accountId, long cutoff) {
        // Each close or reassessment stores all closed days for exactly one account.
        return journal.entries(cutoff).stream()
                .filter(entry -> entry.accruals().stream().anyMatch(accrual -> accrual.accountId().equals(accountId)))
                .reduce((previous, next) -> next);
    }

    private void requireUncapitalized(String accountId, long cutoff) {
        boolean capitalized = journal.entries(cutoff).stream()
                .anyMatch(entry -> entry.request().data() instanceof Request.InterestCapitalization capitalization
                        && capitalization.accountId().equals(accountId));
        if (capitalized) {
            throw new IllegalArgumentException("Interest has already been capitalized for account: " + accountId);
        }
    }
}
