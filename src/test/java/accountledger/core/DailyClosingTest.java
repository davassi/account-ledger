package accountledger.core;

import accountledger.domain.Account;
import accountledger.domain.Money;
import accountledger.domain.Posting;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static accountledger.domain.enums.CurrencyUnit.AED;
import static accountledger.domain.enums.CurrencyUnit.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DailyClosingTest {
    private final Journal journal = new Journal(
            new Account("ACC-001", Money.of(AED, "0.00")),
            new Account("ACC-002", Money.of(BHD, "0.000")));
    private final DailyClosing closing = new DailyClosing(journal);

    @Test
    void anEarlierFeeCanMakeAnotherDayNegative() {
        closing.close("C1", 1, "ACC-001");
        var originalClose = closing.close("C2", 2, "ACC-001");
        var late = journal.append("LATE", 3, List.of(aed(1, "-10.00"), aed(2, "20.00")));
        var revision = closing.reassess("REASSESS", 3, "ACC-001");

        assertEquals(Money.of(AED, "10.00"), journal.balance("ACC-001", 2, late.sequence()));
        assertEquals(List.of(aed(1, "-25.00"), aed(2, "-25.00")), revision.postings());
        assertEquals(late.sequence() + 1, revision.sequence());
        assertEquals(Money.of(AED, "-40.00"), journal.balance("ACC-001", 2, revision.sequence()));
        assertEquals(Money.of(AED, "0.00"), journal.balance("ACC-001", 2, originalClose.sequence()));
        var day3 = closing.close("C3", 3, "ACC-001");
        assertEquals(List.of(aed(3, "-25.00")), day3.postings());
        assertEquals(Money.of(AED, "-65.00"), journal.balance("ACC-001", 3, day3.sequence()));
    }

    @Test
    void closingAlsoCatchesUpAnyUnassessedPastDays() {
        closing.close("C1", 1, "ACC-001");
        journal.append("LATE", 2, List.of(aed(1, "-10.00")));
        var day2 = closing.close("C2", 2, "ACC-001");

        assertEquals(List.of(aed(1, "-25.00"), aed(2, "-25.00")), day2.postings());
        assertEquals(Money.of(AED, "-60.00"), journal.balance("ACC-001", 2, day2.sequence()));
    }

    @ParameterizedTest
    @CsvSource({"0.00, 0.00, 0", "0.01, 0.01, 0", "-0.01, -25.01, 1"})
    void feesRequireAStrictlyNegativeLedgerBalance(String amount, String expected, int count) {
        journal.append("POSTING", 1, List.of(aed(1, amount)));
        var close = closing.close("C1", 1, "ACC-001");

        assertEquals(count, close.postings().size());
        assertEquals(Money.of(AED, expected), journal.balance("ACC-001", 1, close.sequence()));
    }

    @Test
    void holdsDoNotDetermineOverdraftFees() {
        journal.append("FUND", 1, List.of(aed(1, "200.00")));
        var authorizations = new Authorizations(journal);
        authorizations.authorize("HOLD", 1, "ACC-001", "A", Money.of(AED, "200.00"));
        journal.append("DEBIT", 1, List.of(aed(1, "-200.00")));
        var close = closing.close("C1", 1, "ACC-001");

        assertEquals(List.of(), close.postings());
        assertEquals(Money.of(AED, "0.00"), journal.balance("ACC-001", 1, close.sequence()));
        assertEquals(Money.of(AED, "-200.00"), authorizations.available("ACC-001", 1, close.sequence()));
    }

    @Test
    void futureValuePostingsCannotTriggerFeesEarly() {
        journal.append("FUTURE", 1, List.of(aed(3, "-10.00")));
        closing.close("C1", 1, "ACC-001");
        assertEquals(List.of(), closing.reassess("REASSESS", 2, "ACC-001").postings());
        assertEquals(List.of(), closing.close("C2", 2, "ACC-001").postings());
        assertEquals(List.of(aed(3, "-25.00")), closing.close("C3", 3, "ACC-001").postings());
    }

    @Test
    void retriesAndRebuiltComponentsPreserveClosesAndAssessments() {
        var day1 = closing.close("C1", 1, "ACC-001");
        journal.append("LATE", 2, List.of(aed(1, "-10.00")));
        assertSame(day1, closing.close("C1", 1, "ACC-001"));
        assertEquals(List.of(), day1.postings());

        var revision = closing.reassess("R2", 2, "ACC-001");
        var rebuilt = new DailyClosing(journal);
        var day2 = rebuilt.close("C2", 2, "ACC-001");
        var before = journal.entries();
        assertSame(revision, rebuilt.reassess("R2", 2, "ACC-001"));
        assertSame(day2, closing.close("C2", 2, "ACC-001"));
        assertThrows(IllegalArgumentException.class, () -> rebuilt.close("ANOTHER-C2", 2, "ACC-001"));
        assertEquals(before, journal.entries());
        assertEquals(List.of(), rebuilt.reassess("R3", 3, "ACC-001").postings());
        assertEquals(List.of(aed(1, "-25.00"), aed(2, "-25.00")), rebuilt.fees("ACC-001", journal.lastSequence()));
    }

    @Test
    void anExplicitFeeRefundDoesNotPermitAnotherAssessmentForThatDay() {
        journal.append("DEBIT", 1, List.of(aed(1, "-10.00")));
        var day1 = closing.close("C1", 1, "ACC-001");
        new Reversals(journal).reverse("REFUND", 2, "ACC-001", "C1", 1);
        var revision = closing.reassess("R2", 2, "ACC-001");

        assertEquals(List.of(), revision.postings());
        assertEquals(day1.postings(), closing.fees("ACC-001", revision.sequence()));
        assertEquals(Money.of(AED, "-10.00"), journal.balance("ACC-001", 1, revision.sequence()));
    }

    @Test
    void negativeBhdRequiresAFeePolicyAndDoesNotRecordAPartialClose() {
        journal.append("BHD-DEBIT", 1, List.of(new Posting("ACC-002", 1, Money.of(BHD, "-0.001"))));
        var before = journal.entries();
        assertThrows(IllegalArgumentException.class, () -> closing.close("BHD-C1", 1, "ACC-002"));
        assertEquals(before, journal.entries());

        journal.append("BHD-CREDIT", 1, List.of(new Posting("ACC-002", 1, Money.of(BHD, "0.001"))));
        assertEquals(List.of(), closing.close("BHD-C1", 1, "ACC-002").postings());
    }

    @ParameterizedTest
    @CsvSource({"0, ACC-001", "-1, ACC-001", "2, ACC-001", "1, UNKNOWN"})
    void invalidClosesDoNotConsumeAnEventId(int day, String accountId) {
        assertThrows(IllegalArgumentException.class, () -> closing.close("C1", day, accountId));
        assertEquals(List.of(), journal.entries());
        assertEquals(1, closing.close("C1", 1, "ACC-001").sequence());
    }

    @Test
    void reassessmentCannotPrecedeTheDaysItRechecks() {
        assertThrows(IllegalArgumentException.class, () -> closing.reassess("R1", 1, "ACC-001"));
        closing.close("C1", 1, "ACC-001");
        closing.close("C2", 2, "ACC-001");
        var before = journal.entries();

        assertThrows(IllegalArgumentException.class, () -> closing.reassess("R1", 1, "ACC-001"));
        assertEquals(before, journal.entries());
        assertEquals(List.of(), closing.reassess("R1", 2, "ACC-001").postings());
    }

    @Test
    void changingTheAccountDayOrOperationIsNotARetry() {
        closing.close("C1", 1, "ACC-001");
        var before = journal.entries();

        assertThrows(IllegalArgumentException.class, () -> closing.close("C1", 1, "ACC-002"));
        assertThrows(IllegalArgumentException.class, () -> closing.close("C1", 2, "ACC-001"));
        assertThrows(IllegalArgumentException.class, () -> closing.reassess("C1", 1, "ACC-001"));
        assertEquals(before, journal.entries());
        assertThrows(IllegalArgumentException.class, () -> closing.fees("ACC-001", -1));
        assertThrows(IllegalArgumentException.class, () -> closing.fees("ACC-001", journal.lastSequence() + 1));
    }

    private static Posting aed(int day, String amount) {
        return new Posting("ACC-001", day, Money.of(AED, amount));
    }
}
