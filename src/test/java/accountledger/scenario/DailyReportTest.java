package accountledger.scenario;

import accountledger.core.Account;
import accountledger.core.Authorizations;
import accountledger.core.DailyClosing;
import accountledger.core.Journal;
import accountledger.core.Posting;
import accountledger.core.Replay;
import accountledger.core.Request;
import accountledger.core.Settlements;
import accountledger.domain.Money;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static accountledger.domain.CurrencyUnit.AED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyReportTest {
    @ParameterizedTest
    @CsvSource({
            // Day, AED closing, on hold, available, revised; BHD closing, revised.
            "1, 250.00, 0.00, 250.00, 250.00, 0.000, 0.000",
            "2, 250.00, 200.00, 50.00, 225.00, 0.000, 0.000",
            "3, 650.00, 200.00, 450.00, 625.00, 0.000, 0.000",
            "4, 465.00, 0.00, 465.00, 415.00, 0.000, 0.000",
            "5, -230.00, 0.00, -230.00, 390.00, 0.000, 10.000",
            "6, 390.93, 0.00, 390.93, 390.93, 10.008, 10.008"
    })
    void printsOriginalBalancesAndAvailabilityAlongsideFinalRevisions(int day, String aedClosing,
            String aedOnHold, String aedAvailable, String aedRevised, String bhdClosing, String bhdRevised) {
        var scenario = new Scenario();
        scenario.run();
        var output = day(render(scenario::printReport), day);

        assertEquals(List.of("Account", "Currency", "Closing", "On hold", "Available", "Revised closing",
                "Fees (value day)", "Authorizations"), row(output, "Account"));
        assertEquals(List.of("ACC-001", "AED", aedClosing, aedOnHold, aedAvailable, aedRevised),
                row(output, "ACC-001").subList(0, 6));
        assertEquals(List.of("ACC-002", "BHD", bhdClosing, "0.000", bhdClosing, bhdRevised),
                row(output, "ACC-002").subList(0, 6));
        var table = output.lines().filter(line -> line.startsWith("|") || line.startsWith("+")).toList();
        assertEquals(6, table.size());
        table.forEach(line -> assertEquals(table.getFirst().length(), line.length(), output));
    }

    @Test
    void reportsFeesErrorsAndAuthorizationStatesOnTheirOriginalDays() {
        var scenario = new Scenario();
        scenario.run();
        var entries = scenario.journal().entries();
        var output = render(scenario::printReport);

        assertEquals(6, output.lines().filter(line -> line.startsWith("Day ")).count());
        assertFalse(day(output, 1).contains("Auth-A"));
        assertEquals("Auth-A=APPROVED", row(day(output, 2), "ACC-001").getLast());
        assertEquals("Auth-A=APPROVED", row(day(output, 3), "ACC-001").getLast());
        assertEquals("Auth-A=SETTLED", row(day(output, 4), "ACC-001").getLast());
        List.of(5, 6).forEach(day -> assertEquals("Auth-A=SETTLED; Auth-B=DECLINED",
                row(day(output, day), "ACC-001").getLast()));
        assertEquals("25.00 (Day 2); 25.00 (Day 4); 25.00 (Day 5)", row(day(output, 5), "ACC-001").get(6));
        assertEquals(11, IntStream.rangeClosed(1, 6).boxed()
                .flatMap(day -> Stream.of("ACC-001", "ACC-002").map(account -> row(day(output, day), account)))
                .filter(row -> row.get(6).equals("none")).count());
        assertTrue(day(output, 4).contains("Errors: E6: Unknown authorization: Auth-Z"));
        assertEquals(1, output.lines().filter(line -> line.contains("E6:")).count());
        assertEquals(entries, scenario.journal().entries());
    }

    @Test
    void savedPrefixesSurviveLaterPostingsDecisionsAndRejectedAttempts() {
        var journal = new Journal(new Account("A", Money.of(AED, "250.00")));
        var replay = new Replay(journal);
        var report = new DailyReport(journal, List.of("A"));
        var closing = new DailyClosing(journal);
        new Authorizations(journal).authorize("HOLD", 1, "A", "AUTH", Money.of(AED, "200.00"));
        closing.close("C1", 1, "A");
        report.capture(1, replay.rejections());

        journal.append("LATE", 2, List.of(new Posting("A", 1, Money.of(AED, "400.00"))));
        new Settlements(journal).settle("SETTLE", 2, "A", "AUTH", Money.of(AED, "185.00"), 2);
        var missing = new Request("MISSING", 1,
                new Request.Settlement("A", "UNKNOWN", Money.of(AED, "180.00"), 1));
        assertFalse(replay.process(2, missing));
        assertFalse(replay.process(2, missing));
        closing.close("C2", 2, "A");
        var rejections = new ArrayList<>(replay.rejections());
        report.capture(2, rejections);
        var saved = render(report::print);

        rejections.clear();
        journal.append("LATER", 3, List.of(new Posting("A", 1, Money.of(AED, "400.00"))));
        new Authorizations(journal).authorize("NEW-HOLD", 3, "A", "NEW", Money.of(AED, "200.00"));
        assertFalse(replay.process(3, missing));

        assertEquals(saved, render(report::print));
        assertEquals(List.of("A", "AED", "250.00", "200.00", "50.00", "650.00"),
                row(day(saved, 1), "A").subList(0, 6));
        assertEquals("AUTH=APPROVED", row(day(saved, 1), "A").getLast());
        assertTrue(day(saved, 1).contains("Errors: none"));
        assertEquals("AUTH=SETTLED", row(day(saved, 2), "A").getLast());
        assertTrue(day(saved, 2).contains("Errors: MISSING: Unknown authorization: UNKNOWN; "
                + "MISSING: Unknown authorization: UNKNOWN"));
    }

    @Test
    void acceptedDiagnosticsFollowArrivalOrderEvenWhenTheBookedDayIsEarlier() {
        var journal = new Journal(new Account("A", Money.of(AED, "250.00")));
        var replay = new Replay(journal);
        var report = new DailyReport(journal, List.of("A"));
        var closing = new DailyClosing(journal);
        new Authorizations(journal).authorize("HOLD", 1, "A", "AUTH", Money.of(AED, "300.00"));
        closing.close("C1", 1, "A");
        report.capture(1, replay.rejections());
        assertTrue(replay.process(2, new Request("SETTLE", 1,
                new Request.Settlement("A", "AUTH", Money.of(AED, "50.00"), 2))));
        closing.close("C2", 2, "A");
        report.capture(2, replay.rejections());
        var output = render(report::print);

        assertTrue(day(output, 1).contains("Diagnostics: none"));
        assertTrue(day(output, 2).contains("Diagnostics: SETTLE: DECLINED_AUTHORIZATION"));
        assertTrue(day(output, 2).contains("Errors: none"));
    }

    private static String day(String output, int day) {
        return output.split("(?m)^Day \\d+ \\(sequence \\d+\\)\\R")[day];
    }

    private static List<String> row(String output, String account) {
        var line = output.lines().filter(value -> value.startsWith("| " + account + " ")).findFirst().orElseThrow();
        return Stream.of(line.split("\\|")).skip(1).map(String::strip).toList();
    }

    private static String render(Consumer<PrintStream> print) {
        var bytes = new ByteArrayOutputStream();
        try (var out = new PrintStream(bytes, true, UTF_8)) {
            print.accept(out);
        }
        return bytes.toString(UTF_8);
    }
}
