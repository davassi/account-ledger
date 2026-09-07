package accountledger;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static accountledger.CurrencyUnit.AED;
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
        var request = new Request("E1", 1, new Request.Postings(input));
        var original = journal.append(request, () -> new Journal.Effects(input, List.of()));
        input.clear();

        var retry = new Request("E1", 1, new Request.Postings(List.of(credit)));
        assertSame(original, journal.append(retry, () -> fail("A retry must never recompute effects")));
        assertSame(original, journal.append("E1", 1, List.of(credit)));
        assertEquals(List.of(original), journal.entries());
        assertEquals(retry, original.request());
        assertThrows(UnsupportedOperationException.class, () -> ((Request.Postings) request.data()).postings().clear());
    }

    @Test
    void conflictingInputIsRejectedBeforeCalculatingEffects() {
        var original = journal.append("E1", 1, List.of(credit));
        var conflicts = List.of(
                new Request("E1", 2, new Request.Postings(List.of(credit))),
                new Request("E1", 1, new Request.Postings(List.of())),
                new Request("E1", 1, new Request.Hold("ACC-001", "A", credit.delta())));

        conflicts.forEach(request -> assertThrows(IllegalArgumentException.class,
                () -> journal.append(request, () -> fail("A conflicting ID must never calculate effects"))));
        assertEquals(List.of(original), journal.entries());
    }

    @Test
    void failedComputationOrValidationDoesNotReserveTheEventId() {
        var request = new Request("E1", 1, new Request.Postings(List.of(credit)));
        assertThrows(IllegalStateException.class, () -> journal.append(request, () -> {
            throw new IllegalStateException("Computation failed");
        }));
        assertEquals(List.of(), journal.entries());

        var invalid = new Posting("UNKNOWN", 1, credit.delta());
        assertThrows(IllegalArgumentException.class,
                () -> journal.append(request, () -> new Journal.Effects(List.of(credit, invalid), List.of())));
        assertEquals(List.of(), journal.entries());

        var accepted = journal.append("E1", 1, List.of(credit));
        assertEquals(1, accepted.sequence());
        assertEquals(credit.delta(), journal.balance("ACC-001", 1, accepted.sequence()));
    }
}
