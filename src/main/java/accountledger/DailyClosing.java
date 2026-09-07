package accountledger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Records closes, overdraft fees and interest accruals, preserving all earlier views. */
public final class DailyClosing {
    private static final Money OVERDRAFT_FEE = Money.of(CurrencyUnit.AED, "25.00");
    private final Journal journal;
    private final Interest interest;

    /** 
     * Creates a DailyClosing instance with the specified Journal.
     */
    public DailyClosing(Journal journal) {
        this.journal = Objects.requireNonNull(journal, "journal");
        this.interest = new Interest(journal);
    }

    /** 
     * Closes the next day for this account; the returned sequence includes its fees. 
     */
    public Journal.Entry close(String eventId, int day, String accountId) {
        var request = new Request(eventId, day, new Request.DayClose(accountId));
        return journal.append(request, () -> {
            long cutoff = journal.lastSequence();
            if (day != lastClosedDay(accountId, cutoff) + 1) {
                throw new RequestRejectedException("Close days in order, starting at Day 1");
            }
            return closingEffects(accountId, day, cutoff);
        });
    }

    /** Reassesses closed days after a backdated event; processingDay is when fees are discovered. */
    public Journal.Entry reassess(String eventId, int processingDay, String accountId) {
        var request = new Request(eventId, processingDay, new Request.FeeReassessment(accountId));
        return journal.append(request, () -> {
            long cutoff = journal.lastSequence();
            int throughDay = lastClosedDay(accountId, cutoff);
            if (throughDay == 0 || processingDay < throughDay) {
                throw new RequestRejectedException("Reassessment requires closed days at or before the processing day");
            }
            return closingEffects(accountId, throughDay, cutoff);
        });
    }

    /** Returns the original fee postings, even if later entries compensate them. */
    public List<Posting> fees(String accountId, long knownThroughSequence) {
        return journal.entries(knownThroughSequence).stream()
                .filter(entry -> entry.request().data() instanceof Request.DayClose
                        || entry.request().data() instanceof Request.FeeReassessment)
                .flatMap(entry -> entry.postings().stream())
                .filter(posting -> posting.accountId().equals(accountId))
                .toList();
    }

    private int lastClosedDay(String accountId, long cutoff) {
        return journal.entries(cutoff).stream()
                .filter(entry -> entry.request().data() instanceof Request.DayClose close
                        && close.accountId().equals(accountId))
                .mapToInt(Journal.Entry::bookedDay)
                .max().orElse(0);
    }

    private Journal.Effects closingEffects(String accountId, int throughDay, long cutoff) {
        var fees = feesToAssess(accountId, throughDay, cutoff);
        var accruals = interest.calculate(accountId, throughDay, cutoff, fees);
        return new Journal.Effects(fees, List.of(), List.of(), accruals);
    }

    private List<Posting> feesToAssess(String accountId, int throughDay, long cutoff) {
        var assessedDays = fees(accountId, cutoff).stream().map(Posting::valueDay).collect(Collectors.toSet());
        var newFees = new ArrayList<Posting>();
        // Earlier fees can make a later day negative; calculate in day order before appending anything.
        IntStream.rangeClosed(1, throughDay).forEachOrdered(day -> {
            if (assessedDays.contains(day)) {
                return;
            }
            var balance = journal.balance(accountId, day, cutoff);
            var pendingFees = newFees.stream().map(Posting::delta)
                    .reduce(Money.of(balance.currency(), "0"), Money::add);
            if (balance.add(pendingFees).signum() >= 0) {
                return;
            }
            if (balance.currency() != OVERDRAFT_FEE.currency()) {
                throw new RequestRejectedException("No overdraft fee defined for currency: " + balance.currency());
            }
            newFees.add(new Posting(accountId, day, OVERDRAFT_FEE.negate()));
        });
        return newFees;
    }
}
