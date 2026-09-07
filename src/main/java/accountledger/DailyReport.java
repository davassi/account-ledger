package accountledger;

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
     * Supply the cumulative rejection snapshot from the same replay each time.
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
        out.println("Closing and available use each day's saved close.");
        out.printf("Revised closing uses the final saved close: Day %d (sequence %d).%n", last.day(), last.cutoff());
        IntStream.range(0, days.size()).forEach(index -> {
            var previous = index == 0 ? new Snapshot(0, 0, List.of()) : days.get(index - 1);
            printDay(out, days.get(index), previous, last.cutoff());
        });
    }

    private void printDay(PrintStream out, Snapshot current, Snapshot previous, long revisedCutoff) {
        var authorizations = new Authorizations(journal);
        var closing = new DailyClosing(journal);
        out.printf("%nDay %d (sequence %d)%n", current.day(), current.cutoff());
        accounts.forEach(account -> {
            out.printf("  %s | closing %s | available %s | revised closing %s%n", account,
                    money(journal.balance(account, current.day(), current.cutoff())),
                    money(authorizations.available(account, current.day(), current.cutoff())),
                    money(journal.balance(account, current.day(), revisedCutoff)));
            out.println("    Fees assessed today: " + summarize(closing.fees(account, current.cutoff()).stream()
                    .skip(closing.fees(account, previous.cutoff()).size())
                    .map(fee -> money(fee.delta().negate()) + " (value Day " + fee.valueDay() + ")")));
            out.println("    Authorizations: " + summarize(authorizations.states(current.cutoff()).stream()
                    .filter(auth -> auth.accountId().equals(account))
                    .map(auth -> auth.id() + "=" + auth.status())));
        });
        out.println("  Errors: " + summarize(current.rejections().stream()
                .skip(previous.rejections().size())
                .map(rejection -> rejection.request().eventId() + ": " + rejection.reason())));
        out.println("  Diagnostics: " + summarize(journal.entries(current.cutoff()).stream()
                .filter(entry -> entry.sequence() > previous.cutoff())
                .flatMap(entry -> entry.diagnostics().stream().map(code -> entry.eventId() + ": " + code))));
    }

    private static String money(Money amount) {
        return amount.currency() + " " + amount.amount().toPlainString();
    }

    private static String summarize(Stream<String> values) {
        var result = values.collect(joining("; "));
        return result.isEmpty() ? "none" : result;
    }

    private record Snapshot(int day, long cutoff, List<Rejection> rejections) {}
}
