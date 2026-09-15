package accountledger.core;

import accountledger.domain.Account;
import accountledger.domain.Money;
import accountledger.domain.Posting;

import java.util.List;
import org.junit.jupiter.api.Test;

import static accountledger.domain.enums.CurrencyUnit.AED;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Intentionally excluded from the default suite. Run with -Dtest=FeeTimingCounterexample. */
class FeeTimingCounterexample {
    @Test
    void reversalTimingWouldNotChangeTheFinalBalance() {
        // EXPECTED FAILURE: principal cancels in both paths, but a reversal after
        // close retains the AED 25 fee. This exposes the chosen policy's timing dependence.
        assertEquals(balanceAfterReversal(true), balanceAfterReversal(false));
    }

    private Money balanceAfterReversal(boolean beforeClose) {
        var journal = new Journal(new Account("ACC-001", Money.of(AED, "0.00")));
        var reversals = new Reversals(journal);
        journal.append("DEBIT", 1, List.of(new Posting("ACC-001", 1, Money.of(AED, "-10.00"))));
        if (beforeClose) {
            reversals.reverse("REVERSAL", 1, "ACC-001", "DEBIT", 1);
        }
        new DailyClosing(journal).close("CLOSE", 1, "ACC-001");
        if (!beforeClose) {
            reversals.reverse("REVERSAL", 2, "ACC-001", "DEBIT", 1);
        }
        return journal.balance("ACC-001", 1, journal.lastSequence());
    }
}
