package accountledger.scenario;

import accountledger.core.Authorization;
import accountledger.core.Authorizations;
import accountledger.core.DailyClosing;
import accountledger.core.Interest;
import accountledger.core.Journal;
import accountledger.core.Posting;
import accountledger.core.Rejection;
import accountledger.core.Request;
import accountledger.domain.Money;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static accountledger.core.Authorization.Status.DECLINED;
import static accountledger.core.Authorization.Status.SETTLED;
import static accountledger.domain.CurrencyUnit.AED;
import static accountledger.domain.CurrencyUnit.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioTest {
    @Test
    void theExecutablePreservesEventOrderAndClosesBeforeCapitalizing() {
        var scenario = new Scenario();
        scenario.run();
        var journal = scenario.journal();
        long cutoff = journal.lastSequence();
        var eventIds = IntStream.rangeClosed(1, 10).mapToObj(number -> "E" + number).toList();
        assertEquals(eventIds.stream().filter(id -> !id.equals("E6")).toList(),
                journal.entries().stream().map(Journal.Entry::eventId).filter(eventIds::contains).toList());
        assertEquals(List.of(new Rejection(4, new Request("E6", 4,
                        new Request.Settlement("ACC-001", "Auth-Z", Money.of(AED, "180.00"), 4)),
                "Unknown authorization: Auth-Z")), scenario.rejections());

        var e9 = journal.find("E9", cutoff).orElseThrow();
        var e10 = journal.find("E10", cutoff).orElseThrow();
        assertTrue(e10.sequence() > e9.sequence());
        assertEquals(5, e10.bookedDay());
        assertEquals(List.of(new Posting("ACC-002", 5, Money.of(BHD, "3.334")),
                new Posting("ACC-002", 5, Money.of(BHD, "3.333")),
                new Posting("ACC-002", 5, Money.of(BHD, "3.333"))), e10.postings());

        var lastEvents = List.of("E2", "E3", "E4", "E5", "E8", "E10");
        IntStream.rangeClosed(1, 6).forEach(day -> {
            var lastEvent = journal.find(lastEvents.get(day - 1), cutoff).orElseThrow();
            List.of("ACC-001", "ACC-002").forEach(account -> {
                var close = journal.find("CLOSE-" + account + "-" + day, cutoff).orElseThrow();
                assertTrue(close.sequence() > lastEvent.sequence());
                if (day < 6) {
                    var nextEvent = journal.find(List.of("E3", "E4", "E5", "E7", "E9").get(day - 1), cutoff).orElseThrow();
                    assertTrue(close.sequence() < nextEvent.sequence());
                }
            });
        });
        long lastClose = journal.find("CLOSE-ACC-002-6", cutoff).orElseThrow().sequence();
        var capitalizations = journal.entries().stream()
                .filter(entry -> entry.request().data() instanceof Request.InterestCapitalization).toList();
        assertEquals(2, capitalizations.size());
        capitalizations.forEach(entry -> assertTrue(entry.sequence() > lastClose));
    }

    @Test
    void eachRunStartsFromTheSameOpeningBalances() {
        var first = new Scenario();
        first.run();
        var second = new Scenario();
        second.run();

        assertEquals(first.journal().entries(), second.journal().entries());
        assertEquals(first.rejections(), second.rejections());
    }

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

        assertEquals(Money.of(AED, "-370.00"), journal.balance("ACC-001", 2, e7.sequence()));
        assertEquals(e7.sequence() + 1, revision.sequence());
        assertEquals(5, revision.bookedDay());
        assertEquals(List.of(aed(2, "-25.00"), aed(4, "-25.00")), revision.postings());
        assertEquals(Money.of(AED, "5.00"), journal.balance("ACC-001", 3, revision.sequence()));
        assertEquals(Money.of(AED, "-205.00"), authorizations.available("ACC-001", 5, revision.sequence()));
        assertEquals(List.of("0.10", "0.00", "0.00", "0.00"), interest.accruals("ACC-001", revision.sequence())
                .stream().map(accrual -> accrual.amount().amount().toPlainString()).toList());
        assertEquals(List.of(aed(5, "-25.00")), day5.postings());
        assertEquals(Money.of(AED, "-230.00"), journal.balance("ACC-001", 5, day5.sequence()));
        assertEquals(List.of(), journal.find("E9-FEES", finalCutoff).orElseThrow().postings());
        assertEquals(List.of(), journal.find("E10-FEES", finalCutoff).orElseThrow().postings());

        var expected = List.of("250.00", "225.00", "625.00", "415.00", "390.00", "390.00");
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
        assertEquals(List.of("0.10", "0.09", "0.25", "0.17", "0.16", "0.16"), dailyAed.stream()
                .map(accrual -> accrual.amount().amount().toPlainString()).toList());
        assertEquals(List.of("0.000", "0.000", "0.000", "0.000", "0.004", "0.004"),
                interest.accruals("ACC-002", cutoff).stream()
                        .map(accrual -> accrual.amount().amount().toPlainString()).toList());
        assertEquals(Money.of(AED, "0.10"), interest.accruals("ACC-001", day2.sequence()).getLast().amount());
        assertEquals(Money.of(BHD, "0.000"), interest.accruals("ACC-002", bhdDay5.sequence()).getLast().amount());

        var aedCapitalization = journal.find("INTEREST-ACC-001", finalCutoff).orElseThrow();
        var bhdCapitalization = journal.find("INTEREST-ACC-002", finalCutoff).orElseThrow();
        assertEquals(List.of(aed(6, "0.93")), aedCapitalization.postings());
        assertEquals(List.of(new Posting("ACC-002", 6, Money.of(BHD, "0.008"))), bhdCapitalization.postings());
        assertEquals(Money.of(AED, "390.93"), journal.balance("ACC-001", 6, journal.lastSequence()));
        assertEquals(Money.of(BHD, "10.008"), journal.balance("ACC-002", 6, journal.lastSequence()));
        assertEquals(dailyAed, interest.accruals("ACC-001", journal.lastSequence()));
        assertEquals(3, closing.fees("ACC-001", journal.lastSequence()).size());
        assertEquals(Money.of(AED, "390.00"), journal.balance("ACC-001", 6, cutoff));
    }

    private static Posting aed(int day, String amount) {
        return new Posting("ACC-001", day, Money.of(AED, amount));
    }
}
