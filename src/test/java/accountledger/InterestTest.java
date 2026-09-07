package accountledger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static accountledger.Authorization.Status.DECLINED;
import static accountledger.CurrencyUnit.AED;
import static accountledger.CurrencyUnit.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InterestTest {
    private Journal journal;
    private DailyClosing closing;
    private Interest interest;

    @ParameterizedTest
    @CsvSource({"AED, -0.01, 0.00", "AED, 0.00, 0.00", "AED, 0.01, 0.00",
            "AED, 12.50, 0.00", "AED, 37.50, 0.02", "BHD, 0.000, 0.000",
            "BHD, 1.250, 0.000", "BHD, 3.750, 0.002"})
    void accrualsRequirePositiveBalancesAndUseHalfEven(CurrencyUnit currency, String opening, String expected) {
        openAccount(currency, opening);
        var close = closing.close("C1", 1, "A");

        assertEquals(List.of(new Accrual("A", 1, Money.of(currency, expected))), close.accruals());
        assertEquals(close.accruals(), interest.accruals("A", close.sequence()));
        assertEquals(List.of(), interest.accruals("A", 0));
    }

    @ParameterizedTest
    @CsvSource({"AED, 37.50, 0.12, 37.62", "BHD, 3.750, 0.012, 3.762"})
    void capitalizationSumsRoundedDaysWithoutRoundingTheRawTotal(CurrencyUnit currency, String opening,
                                                               String total, String expectedBalance) {
        openAccount(currency, opening);
        closeThrough(6);
        long cutoff = journal.lastSequence();
        var daily = interest.accruals("A", cutoff);
        var capitalization = interest.capitalize("CAP", "A");

        // Rounding the six unrounded products together would give 0.09 AED / 0.009 BHD.
        var roundedSum = daily.stream().map(Accrual::amount).reduce(Money.of(currency, "0"), Money::add);
        assertEquals(Money.of(currency, total), roundedSum);
        assertEquals(List.of(new Posting("A", 6, roundedSum)), capitalization.postings());
        assertEquals(6, capitalization.bookedDay());
        assertEquals(Money.of(currency, opening), journal.balance("A", 6, cutoff));
        assertEquals(Money.of(currency, opening), journal.balance("A", 5, capitalization.sequence()));
        assertEquals(Money.of(currency, expectedBalance), journal.balance("A", 6, capitalization.sequence()));
        assertEquals(daily, interest.accruals("A", capitalization.sequence()));
    }

    @Test
    void holdsDoNotReduceInterestAndAccrualsCannotBeSpent() {
        openAccount(AED, "250.00");
        var authorizations = new Authorizations(journal);
        authorizations.authorize("HOLD", 1, "A", "FULL", Money.of(AED, "250.00"));
        var close = closing.close("C1", 1, "A");

        assertEquals(Money.of(AED, "0.10"), close.accruals().getFirst().amount());
        assertEquals(Money.of(AED, "250.00"), journal.balance("A", 1, close.sequence()));
        assertEquals(Money.of(AED, "0.00"), authorizations.available("A", 1, close.sequence()));
        assertEquals(DECLINED, authorizations.authorize("EXTRA", 1, "A", "CENT", Money.of(AED, "0.01")).status());
    }

    @Test
    void revisionsIncludeNewFeesOnlyFromTheirValueDayAndKeepEarlierSnapshots() {
        openAccount(AED, "0.00");
        journal.append("FUND", 1, List.of(new Posting("A", 1, Money.of(AED, "100.00"))));
        closeThrough(3);
        long originalCutoff = journal.lastSequence();
        var original = interest.accruals("A", originalCutoff);
        journal.append("LATE", 4, List.of(new Posting("A", 2, Money.of(AED, "-110.00")),
                new Posting("A", 3, Money.of(AED, "100.00"))));
        var revision = closing.reassess("REVISION", 4, "A");

        assertEquals(List.of(new Accrual("A", 1, Money.of(AED, "0.04")),
                new Accrual("A", 2, Money.of(AED, "0.00")),
                new Accrual("A", 3, Money.of(AED, "0.03"))), revision.accruals());
        assertEquals(original, interest.accruals("A", originalCutoff));
        assertEquals(Money.of(AED, "0.04"), original.get(1).amount());
        assertThrows(UnsupportedOperationException.class, () -> original.clear());
    }

    @Test
    void capitalizationRequiresAnUpToDateCloseForEveryDay() {
        openAccount(AED, "250.00");
        assertThrows(IllegalArgumentException.class, () -> interest.capitalize("CAP", "A"));
        closeThrough(5);
        assertThrows(IllegalArgumentException.class, () -> interest.capitalize("CAP", "A"));
        closing.close("C6", 6, "A");
        journal.append("LATE", 6, List.of(new Posting("A", 5, Money.of(AED, "250.00"))));
        var before = journal.entries();

        assertThrows(IllegalArgumentException.class, () -> interest.capitalize("CAP", "A"));
        assertEquals(before, journal.entries());
        closing.reassess("REFRESH", 6, "A");
        assertEquals(List.of(new Posting("A", 6, Money.of(AED, "0.80"))), interest.capitalize("CAP", "A").postings());
    }

    @Test
    void retriesStayStableAndCapitalizationFreezesAccruals() {
        openAccount(AED, "250.00");
        closeThrough(6);
        var originalClose = journal.find("C6", journal.lastSequence()).orElseThrow();
        var capitalization = interest.capitalize("CAP", "A");
        var daily = interest.accruals("A", journal.lastSequence());
        // Low-level postings remain append-only; reopening finalized interest is outside this window.
        journal.append("AFTER", 6, List.of(new Posting("A", 1, Money.of(AED, "250.00"))));
        var before = journal.entries();

        assertSame(capitalization, new Interest(journal).capitalize("CAP", "A"));
        assertSame(originalClose, closing.close("C6", 6, "A"));
        assertThrows(IllegalArgumentException.class, () -> interest.capitalize("SECOND-CAP", "A"));
        assertThrows(IllegalArgumentException.class, () -> closing.reassess("REFRESH", 6, "A"));
        assertThrows(IllegalArgumentException.class, () -> closing.close("C7", 7, "A"));
        assertThrows(IllegalArgumentException.class, () -> journal.append("CAP", 6, capitalization.postings()));
        assertEquals(before, journal.entries());
        assertEquals(daily, new Interest(journal).accruals("A", journal.lastSequence()));
    }

    @Test
    void otherAccountsAndFutureValuePostingsDoNotMakeTheAccrualsStale() {
        openAccount(AED, "250.00");
        closeThrough(6);
        journal.append("OTHER", 6, List.of(new Posting("B", 1, Money.of(BHD, "10.000"))));
        journal.append("FUTURE", 6, List.of(new Posting("A", 7, Money.of(AED, "250.00"))));

        assertEquals(List.of(new Posting("A", 6, Money.of(AED, "0.60"))), interest.capitalize("CAP", "A").postings());
        assertThrows(IllegalArgumentException.class, () -> interest.capitalize("CAP", "B"));
        assertEquals(List.of(), interest.accruals("B", journal.lastSequence()));
    }

    @Test
    void zeroInterestStillRecordsOneFinalization() {
        openAccount(AED, "0.00");
        closeThrough(6);
        assertEquals(List.of(new Posting("A", 6, Money.of(AED, "0.00"))), interest.capitalize("CAP", "A").postings());
        assertThrows(IllegalArgumentException.class, () -> interest.capitalize("SECOND-CAP", "A"));
    }

    @Test
    void accrualEffectsAreValidatedAndDefensivelyCopied() {
        openAccount(AED, "0.00");
        var request = new Request("FACT", 1, new Request.DayClose("A"));
        var daily = new ArrayList<>(List.of(new Accrual("A", 1, Money.of(AED, "0.10"))));
        var effects = new Journal.Effects(List.of(), List.of(), List.of(), daily);
        daily.clear();
        var entry = journal.append(request, () -> effects);
        assertEquals(1, entry.accruals().size());
        assertThrows(UnsupportedOperationException.class, () -> entry.accruals().clear());
        assertThrows(IllegalArgumentException.class, () -> interest.accruals("A", -1));
        assertThrows(IllegalArgumentException.class, () -> interest.accruals("A", journal.lastSequence() + 1));

        var invalid = new Request("INVALID", 1, new Request.DayClose("A"));
        assertThrows(IllegalArgumentException.class, () -> journal.append(invalid,
                () -> new Journal.Effects(List.of(), List.of(), List.of(),
                        List.of(new Accrual("A", 1, Money.of(BHD, "0.100"))))));
        assertThrows(IllegalArgumentException.class, () -> journal.append(invalid,
                () -> new Journal.Effects(List.of(), List.of(), List.of(),
                        List.of(new Accrual("UNKNOWN", 1, Money.of(AED, "0.10"))))));
        assertEquals(List.of(entry), journal.entries());
        assertThrows(IllegalArgumentException.class, () -> new Accrual("A", 1, Money.of(AED, "-0.01")));
        assertThrows(IllegalArgumentException.class, () -> new Accrual("A", 0, Money.of(AED, "0.00")));
        assertThrows(IllegalArgumentException.class, () -> new Accrual(" ", 1, Money.of(AED, "0.00")));
    }

    private void openAccount(CurrencyUnit currency, String opening) {
        journal = new Journal(new Account("A", Money.of(currency, opening)), new Account("B", Money.of(BHD, "0.000")));
        closing = new DailyClosing(journal);
        interest = new Interest(journal);
    }

    private void closeThrough(int day) {
        IntStream.rangeClosed(1, day).forEach(valueDay -> closing.close("C" + valueDay, valueDay, "A"));
    }
}
