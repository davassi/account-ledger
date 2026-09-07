package accountledger;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static accountledger.Authorization.Status.DECLINED;
import static accountledger.Authorization.Status.SETTLED;
import static accountledger.CurrencyUnit.AED;
import static accountledger.CurrencyUnit.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DailyClosingTest {
    private final Journal journal = new Journal(
            new Account("ACC-001", Money.of(AED, "0.00")),
            new Account("ACC-002", Money.of(BHD, "0.000")));
    private final DailyClosing closing = new DailyClosing(journal);

    @Test
    void theSixDayStreamAssessesThreeFeesAndKeepsThemAfterE9() {
        var scenario = new Scenario();
        scenario.run();
        var journal = scenario.journal();
        var closing = new DailyClosing(journal);
        var authorizations = new Authorizations(journal);
        var interest = new Interest(journal);
        long finalCutoff = journal.lastSequence();
        var day2 = journal.find("CLOSE-ACC-001-2", finalCutoff).orElseThrow();
        var day5 = journal.find("CLOSE-ACC-001-5", finalCutoff).orElseThrow();
        var e7 = journal.find("E7", finalCutoff).orElseThrow();
        var revision = journal.find("E7-FEES", finalCutoff).orElseThrow();
        long cutoff = journal.find("CLOSE-ACC-002-6", finalCutoff).orElseThrow().sequence();

        assertEquals(List.of(), scenario.rejections());
        assertEquals(Money.of(AED, "-370.00"), journal.balance("ACC-001", 2, e7.sequence()));
        assertEquals(e7.sequence() + 1, revision.sequence());
        assertEquals(5, revision.bookedDay());
        assertEquals(List.of(aed(2, "-25.00"), aed(4, "-25.00")), revision.postings());
        assertEquals(Money.of(AED, "5.00"), journal.balance("ACC-001", 3, revision.sequence()));
        assertEquals(Money.of(AED, "-385.00"), authorizations.available("ACC-001", 5, revision.sequence()));
        assertEquals(List.of("0.10", "0.00", "0.00", "0.00"), interest.accruals("ACC-001", revision.sequence())
                .stream().map(accrual -> accrual.amount().amount().toPlainString()).toList());
        assertEquals(List.of(aed(5, "-25.00")), day5.postings());
        assertEquals(Money.of(AED, "-410.00"), journal.balance("ACC-001", 5, day5.sequence()));
        assertEquals(List.of(), journal.find("E9-FEES", finalCutoff).orElseThrow().postings());
        assertEquals(List.of(), journal.find("E10-FEES", finalCutoff).orElseThrow().postings());

        var expected = List.of("250.00", "225.00", "625.00", "235.00", "210.00", "210.00");
        IntStream.rangeClosed(1, 6).forEach(day ->
                assertEquals(Money.of(AED, expected.get(day - 1)), journal.balance("ACC-001", day, cutoff)));
        assertEquals(List.of(aed(2, "-25.00"), aed(4, "-25.00"), aed(5, "-25.00")), closing.fees("ACC-001", cutoff));
        assertEquals(List.of(), closing.fees("ACC-002", cutoff));
        assertEquals(Money.of(BHD, "10.000"), journal.balance("ACC-002", 5, cutoff));
        assertEquals(List.of(SETTLED, DECLINED), authorizations.states(cutoff).stream().map(Authorization::status).toList());
        assertEquals(Money.of(AED, "250.00"), journal.balance("ACC-001", 2, day2.sequence()));
        assertEquals(Money.of(AED, "50.00"), authorizations.available("ACC-001", 2, day2.sequence()));
        assertEquals(List.of(), closing.fees("ACC-001", day2.sequence()));
        var bhdDay5 = journal.find("CLOSE-ACC-002-5", cutoff).orElseThrow();
        assertEquals(Money.of(BHD, "0.000"), journal.balance("ACC-002", 5, bhdDay5.sequence()));
        assertThrows(UnsupportedOperationException.class, () -> closing.fees("ACC-001", cutoff).clear());

        var dailyAed = interest.accruals("ACC-001", cutoff);
        assertEquals(List.of("0.10", "0.09", "0.25", "0.09", "0.08", "0.08"), dailyAed.stream()
                .map(accrual -> accrual.amount().amount().toPlainString()).toList());
        assertEquals(List.of("0.000", "0.000", "0.000", "0.000", "0.004", "0.004"),
                interest.accruals("ACC-002", cutoff).stream()
                        .map(accrual -> accrual.amount().amount().toPlainString()).toList());
        assertEquals(Money.of(AED, "0.10"), interest.accruals("ACC-001", day2.sequence()).getLast().amount());
        assertEquals(Money.of(BHD, "0.000"), interest.accruals("ACC-002", bhdDay5.sequence()).getLast().amount());

        var aedCapitalization = journal.find("INTEREST-ACC-001", finalCutoff).orElseThrow();
        var bhdCapitalization = journal.find("INTEREST-ACC-002", finalCutoff).orElseThrow();
        assertEquals(List.of(aed(6, "0.69")), aedCapitalization.postings());
        assertEquals(List.of(new Posting("ACC-002", 6, Money.of(BHD, "0.008"))), bhdCapitalization.postings());
        assertEquals(Money.of(AED, "210.69"), journal.balance("ACC-001", 6, journal.lastSequence()));
        assertEquals(Money.of(BHD, "10.008"), journal.balance("ACC-002", 6, journal.lastSequence()));
        assertEquals(dailyAed, interest.accruals("ACC-001", journal.lastSequence()));
        assertEquals(3, closing.fees("ACC-001", journal.lastSequence()).size());
        assertEquals(Money.of(AED, "210.00"), journal.balance("ACC-001", 6, cutoff));
    }

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
