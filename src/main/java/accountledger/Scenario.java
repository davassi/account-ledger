package accountledger;

import java.util.Arrays;
import java.util.List;

import static accountledger.CurrencyUnit.AED;
import static accountledger.CurrencyUnit.BHD;

/** The required six-day scenario, in arrival order, with explicit closing operations. */
public final class Scenario {
    private static final List<String> ACCOUNTS = List.of("ACC-001", "ACC-002");
    private final Journal journal = new Journal(
            new Account("ACC-001", Money.of(AED, "0.00")),
            new Account("ACC-002", Money.of(BHD, "0.000")));
    private final Replay replay = new Replay(journal);

    public static void main(String[] args) {
        var scenario = new Scenario();
        scenario.run();
        scenario.printSummary();
    }

    void run() {
        process(1,
                new Request("E1", 1, aed(1, "1200.00")),
                new Request("E2", 1, aed(1, "-950.00")));
        closeDay(1);
        process(2, new Request("E3", 2, new Request.Hold("ACC-001", "Auth-A", Money.of(AED, "200.00"))));
        closeDay(2);
        process(3, new Request("E4", 3, aed(3, "400.00")));
        closeDay(3);
        process(4,
                new Request("E5", 4, new Request.Settlement("ACC-001", "Auth-A", Money.of(AED, "185.00"), 4)),
                new Request("E6", 4, new Request.Settlement("ACC-001", "Auth-Z", Money.of(AED, "180.00"), 4)));
        closeDay(4);
        process(5,
                new Request("E7", 5, aed(2, "-620.00")),
                new Request("E7-FEES", 5, new Request.FeeReassessment("ACC-001")),
                new Request("E8", 5, new Request.Hold("ACC-001", "Auth-B", Money.of(AED, "90.00"))));
        closeDay(5);

        var installments = Money.of(BHD, "10.000").split(3).stream()
                .map(amount -> new Posting("ACC-002", 5, amount)).toList();
        process(6,
                new Request("E9", 6, new Request.Reversal("ACC-001", "E7", 2)),
                new Request("E9-FEES", 6, new Request.FeeReassessment("ACC-001")),
                new Request("E10", 5, new Request.Postings(installments)), // Arrives on Day 6, still booked Day 5.
                new Request("E10-FEES", 6, new Request.FeeReassessment("ACC-002")));
        closeDay(6);
        ACCOUNTS.forEach(account -> process(6,
                new Request("INTEREST-" + account, 6, new Request.InterestCapitalization(account))));
    }

    Journal journal() { return journal; }
    List<Rejection> rejections() { return replay.rejections(); }

    private void process(int day, Request... requests) {
        Arrays.stream(requests).forEach(request -> replay.process(day, request));
    }

    private void closeDay(int day) {
        ACCOUNTS.forEach(account -> process(day,
                new Request("CLOSE-" + account + "-" + day, day, new Request.DayClose(account))));
    }

    private static Request.Postings aed(int valueDay, String amount) {
        return new Request.Postings(List.of(new Posting("ACC-001", valueDay, Money.of(AED, amount))));
    }

    private void printSummary() {
        long cutoff = journal.lastSequence();
        System.out.println("Final balances after Day 6 capitalization:");
        ACCOUNTS.forEach(account -> {
            var balance = journal.balance(account, 6, cutoff);
            System.out.printf("%s: %s %s%n", account, balance.currency(), balance.amount().toPlainString());
        });
        new Authorizations(journal).states(cutoff).forEach(auth ->
                System.out.printf("%s %s: %s%n", auth.accountId(), auth.id(), auth.status()));
        journal.entries(cutoff).forEach(entry -> entry.diagnostics().forEach(diagnostic ->
                System.out.printf("%s: %s%n", entry.eventId(), diagnostic)));
        System.out.printf("Rejected attempts: %d%n", replay.rejections().size());
        replay.rejections().forEach(System.out::println);
    }
}
