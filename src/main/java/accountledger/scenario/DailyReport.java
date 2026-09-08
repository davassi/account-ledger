package accountledger.scenario;

import accountledger.core.Authorizations;
import accountledger.core.DailyClosing;
import accountledger.core.Journal;
import accountledger.core.Rejection;
import accountledger.domain.Money;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

/** Prints saved daily closes alongside balances revised at the last saved close. */
public final class DailyReport {
    private final Journal journal;
    private final List<String> accounts;
    private final List<Snapshot> days = new ArrayList<>();

    public DailyReport(Journal journal, List<String> accounts) {
        this.journal = Objects.requireNonNull(journal, "journal");
        this.accounts = List.copyOf(accounts);
    }

    /**
     * Save once per day, in order, after all end-of-day operations including capitalization.
     * Supply the cumulative rejection snapshot from the same command processor each time.
     */
    public void capture(int day, List<Rejection> rejections) {
        days.add(new Snapshot(day, journal.lastSequence(), List.copyOf(rejections)));
    }

    /** Reads only saved prefixes; printing never changes the journal or its decisions. */
    public void print(PrintStream out) {
        if (days.isEmpty()) {
            return;
        }
        var last = days.getLast();
        IntStream.range(0, days.size()).forEach(index -> {
            var previous = index == 0 ? new Snapshot(0, 0, List.of()) : days.get(index - 1);
            printDay(out, days.get(index), previous, last.cutoff());
        });
    }

    private void printDay(PrintStream out, Snapshot current, Snapshot previous, long revisedCutoff) {
        var authorizations = new Authorizations(journal);
        var closing = new DailyClosing(journal);
        out.printf("%nDay %d (sequence %d)%n", current.day(), current.cutoff());
        var rows = accounts.stream().map(account -> {
            var balance = journal.balance(account, current.day(), current.cutoff());
            var available = authorizations.available(account, current.day(), current.cutoff());
            var onHold = balance.subtract(available);
            var fees = summarize(closing.fees(account, current.cutoff()).stream()
                    .skip(closing.fees(account, previous.cutoff()).size())
                    .map(fee -> amount(fee.delta().negate()) + " (Day " + fee.valueDay() + ")"));
            var states = summarize(authorizations.states(current.cutoff()).stream()
                    .filter(auth -> auth.accountId().equals(account))
                    .map(auth -> auth.id() + "=" + auth.status()));
            return List.of(account, balance.currency().name(), amount(balance),
                    amount(onHold), amount(available),
                    amount(journal.balance(account, current.day(), revisedCutoff)), fees, states);
        }).toList();
        printAccounts(out, rows);
        out.println("  Errors: " + summarize(current.rejections().stream()
                .skip(previous.rejections().size())
                .map(rejection -> rejection.command().eventId() + ": " + rejection.reason())));
        out.println("  Diagnostics: " + summarize(journal.entries(current.cutoff()).stream()
                .filter(entry -> entry.sequence() > previous.cutoff())
                .flatMap(entry -> entry.diagnostics().stream().map(code -> entry.eventId() + ": " + code))));
    }

    private static void printAccounts(PrintStream out, List<List<String>> rows) {
        var headers = List.of("Account", "Currency", "Closing", "On hold", "Available", "Revised closing",
                "Fees (value day)", "Authorizations");
        var widths = IntStream.range(0, headers.size()).map(column ->
                Stream.concat(Stream.of(headers), rows.stream())
                        .mapToInt(row -> row.get(column).length()).max().orElseThrow()).toArray();
        // Balances and held amounts are right-aligned; labels and states are left-aligned.
        var format = IntStream.range(0, widths.length)
                .mapToObj(column -> "%" + (column >= 2 && column <= 5 ? "" : "-") + widths[column] + "s")
                .collect(joining(" | ", "| ", " |%n"));
        var border = IntStream.of(widths).mapToObj(width -> "-".repeat(width + 2))
                .collect(joining("+", "+", "+"));
        out.println(border);
        out.printf(format, headers.toArray());
        out.println(border);
        rows.forEach(row -> out.printf(format, row.toArray()));
        out.println(border);
    }

    private static String amount(Money money) {
        return money.amount().toPlainString();
    }

    private static String summarize(Stream<String> values) {
        var result = values.collect(joining("; "));
        return result.isEmpty() ? "none" : result;
    }

    private record Snapshot(int day, long cutoff, List<Rejection> rejections) {}
}
