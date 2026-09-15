package accountledger.scenario;

import accountledger.domain.Account;
import accountledger.core.Journal;
import accountledger.domain.Posting;
import accountledger.domain.Rejection;
import accountledger.core.CommandProcessor;
import accountledger.domain.Command;
import accountledger.domain.Money;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import static accountledger.domain.enums.CurrencyUnit.AED;
import static accountledger.domain.enums.CurrencyUnit.BHD;

/** The required six-day scenario, in arrival order, with explicit closing operations. */
public final class Scenario {

    private static final List<String> ACCOUNTS = List.of("ACC-001", "ACC-002");

    private final Journal journal = new Journal(
            new Account("ACC-001", Money.of(AED, "0.00")),
            new Account("ACC-002", Money.of(BHD, "0.000")));

    private final CommandProcessor processor = new CommandProcessor(journal);
    private final DailyReport report = new DailyReport(journal, ACCOUNTS);

    public static void main(String[] args) {
        var scenario = new Scenario();
        scenario.run();
        scenario.printReport(System.out);
    }

    /** Runs the scenario with the specified commands and closing operations. */
    void run() {
        process(1,
                new Command("E1", 1, aed(1, "1200.00")),
                new Command("E2", 1, aed(1, "-950.00")));
        endDay(1);
        process(2, new Command("E3", 2, new Command.Hold("ACC-001", "Auth-A", Money.of(AED, "200.00"))));
        endDay(2);
        process(3, new Command("E4", 3, aed(3, "400.00")));
        endDay(3);
        process(4,
                new Command("E5", 4, new Command.Settlement("ACC-001", "Auth-A", Money.of(AED, "185.00"), 4)),
                new Command("E6", 4, new Command.Settlement("ACC-001", "Auth-Z", Money.of(AED, "180.00"), 4)));
        endDay(4);
        process(5,
                new Command("E7", 5, aed(2, "-620.00")),
                new Command("E7-FEES", 5, new Command.FeeReassessment("ACC-001")),
                new Command("E8", 5, new Command.Hold("ACC-001", "Auth-B", Money.of(AED, "90.00"))));
        endDay(5);

        var installments = Money.of(BHD, "10.000").split(3).stream()
                .map(amount -> new Posting("ACC-002", 5, amount)).toList();
        process(6,
                new Command("E9", 6, new Command.Reversal("ACC-001", "E7", 2)),
                new Command("E9-FEES", 6, new Command.FeeReassessment("ACC-001")),
                new Command("E10", 5, new Command.Postings(installments)), // Arrives on Day 6, still booked Day 5.
                new Command("E10-FEES", 6, new Command.FeeReassessment("ACC-002")));
        endDay(6);
    }

    Journal journal() { return journal; }
    List<Rejection> rejections() { return processor.rejections(); }
    void printReport(PrintStream out) { report.print(out); }

    private void process(int day, Command... commands) {
        Arrays.stream(commands).forEach(command -> processor.process(day, command));
    }

    private void endDay(int day) {
        ACCOUNTS.forEach(account -> process(day,
                new Command("CLOSE-" + account + "-" + day, day, new Command.DayClose(account))));
        if (day == 6) {
            ACCOUNTS.forEach(account -> process(day,
                    new Command("INTEREST-" + account, day, new Command.InterestCapitalization(account))));
        }
        report.capture(day, processor.rejections());
    }

    private static Command.Postings aed(int valueDay, String amount) {
        return new Command.Postings(List.of(new Posting("ACC-001", valueDay, Money.of(AED, amount))));
    }
}
