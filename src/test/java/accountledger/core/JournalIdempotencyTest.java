package accountledger.core;

import accountledger.domain.Money;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static accountledger.domain.CurrencyUnit.AED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class JournalIdempotencyTest {
    private final Journal journal = new Journal(new Account("ACC-001", Money.of(AED, "0.00")));
    private final Posting credit = new Posting("ACC-001", 1, Money.of(AED, "1200.00"));

    @Test
    void onlyANewRequestCalculatesEffects() {
        var input = new ArrayList<>(List.of(credit));
        var command = new Command("E1", 1, new Command.Postings(input));
        var original = journal.append(command, () -> new Journal.Effects(input, List.of()));
        input.clear();

        var retry = new Command("E1", 1, new Command.Postings(List.of(credit)));
        assertSame(original, journal.append(retry, () -> fail("A retry must never recompute effects")));
        assertSame(original, journal.append("E1", 1, List.of(credit)));
        assertEquals(List.of(original), journal.entries());
        assertEquals(retry, original.command());
        assertThrows(UnsupportedOperationException.class, () -> ((Command.Postings) command.data()).postings().clear());
    }

    @Test
    void conflictingInputIsRejectedBeforeCalculatingEffects() {
        var original = journal.append("E1", 1, List.of(credit));
        var conflicts = List.of(
                new Command("E1", 2, new Command.Postings(List.of(credit))),
                new Command("E1", 1, new Command.Postings(List.of())),
                new Command("E1", 1, new Command.Hold("ACC-001", "A", credit.delta())));

        conflicts.forEach(command -> assertThrows(IllegalArgumentException.class,
                () -> journal.append(command, () -> fail("A conflicting ID must never calculate effects"))));
        assertEquals(List.of(original), journal.entries());
    }

    @Test
    void failedComputationOrValidationDoesNotReserveTheEventId() {
        var command = new Command("E1", 1, new Command.Postings(List.of(credit)));
        assertThrows(IllegalStateException.class, () -> journal.append(command, () -> {
            throw new IllegalStateException("Computation failed");
        }));
        assertEquals(List.of(), journal.entries());

        var invalid = new Posting("UNKNOWN", 1, credit.delta());
        assertThrows(IllegalArgumentException.class,
                () -> journal.append(command, () -> new Journal.Effects(List.of(credit, invalid), List.of())));
        assertEquals(List.of(), journal.entries());

        var accepted = journal.append("E1", 1, List.of(credit));
        assertEquals(1, accepted.sequence());
        assertEquals(credit.delta(), journal.balance("ACC-001", 1, accepted.sequence()));
    }
}
