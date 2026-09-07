package accountledger;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static accountledger.CurrencyUnit.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioTest {
    @Test
    void theExecutablePreservesEventOrderAndClosesBeforeCapitalizing() {
        var scenario = new Scenario();
        scenario.run();
        var journal = scenario.journal();
        long cutoff = journal.lastSequence();
        var eventIds = IntStream.rangeClosed(1, 10).mapToObj(number -> "E" + number).toList();
        assertEquals(eventIds, journal.entries().stream().map(Journal.Entry::eventId).filter(eventIds::contains).toList());

        var e9 = journal.find("E9", cutoff).orElseThrow();
        var e10 = journal.find("E10", cutoff).orElseThrow();
        assertTrue(e10.sequence() > e9.sequence());
        assertEquals(5, e10.bookedDay());
        assertEquals(List.of(new Posting("ACC-002", 5, Money.of(BHD, "3.334")),
                new Posting("ACC-002", 5, Money.of(BHD, "3.333")),
                new Posting("ACC-002", 5, Money.of(BHD, "3.333"))), e10.postings());

        var lastEvents = List.of("E2", "E3", "E4", "E6", "E8", "E10");
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
        assertEquals(List.of(), second.rejections());
    }
}
