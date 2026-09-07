package accountledger;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static accountledger.Authorization.Status.DECLINED;
import static accountledger.CurrencyUnit.AED;
import static accountledger.CurrencyUnit.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayTest {
    private final Journal journal = new Journal(new Account("A", Money.of(AED, "250.00")));
    private final Replay replay = new Replay(journal);

    @Test
    void aRejectedSettlementKeepsFundsAndHoldAndDoesNotReserveItsEventId() {
        assertTrue(replay.process(1, new Request("HOLD", 1, new Request.Hold("A", "AUTH", Money.of(AED, "200.00")))));
        var before = journal.entries();
        var invalid = new Request("SETTLE", 2, new Request.Settlement("A", "AUTH", Money.of(AED, "0.00"), 2));

        assertFalse(replay.process(2, invalid));
        var failures = replay.rejections();
        assertEquals(List.of(new Rejection(2, invalid,
                "Settlement requires an authorization ID and a positive amount")), failures);
        assertEquals(before, journal.entries());
        assertEquals(Money.of(AED, "250.00"), journal.balance("A", 2, journal.lastSequence()));
        assertEquals(Money.of(AED, "50.00"), new Authorizations(journal).available("A", 2, journal.lastSequence()));

        var corrected = new Request("SETTLE", 2, new Request.Settlement("A", "AUTH", Money.of(AED, "185.00"), 2));
        assertTrue(replay.process(2, corrected));
        assertEquals(Money.of(AED, "65.00"), new Authorizations(journal).available("A", 2, journal.lastSequence()));
        assertEquals(failures, replay.rejections());
    }

    @Test
    void everyFailedAttemptIsRecordedAndTheSameRequestCanLaterSucceed() {
        var reversal = new Request("REVERSE", 5, new Request.Reversal("A", "ORIGINAL", 2));
        assertFalse(replay.process(5, reversal));
        var day5Failures = replay.rejections();
        assertFalse(replay.process(6, reversal));

        assertEquals(List.of(new Rejection(5, reversal, "Unknown original event: ORIGINAL")), day5Failures);
        assertEquals(new Rejection(6, reversal, "Unknown original event: ORIGINAL"), replay.rejections().getLast());
        assertSame(reversal, replay.rejections().getLast().request());
        assertEquals(List.of(), journal.entries());
        assertThrows(UnsupportedOperationException.class, () -> day5Failures.clear());

        assertTrue(replay.process(6, new Request("ORIGINAL", 6,
                new Request.Postings(List.of(new Posting("A", 2, Money.of(AED, "-10.00")))))));
        assertTrue(replay.process(6, reversal));
        var accepted = journal.entries();
        assertTrue(replay.process(6, reversal));
        assertEquals(accepted, journal.entries());
        assertEquals(2, replay.rejections().size());
        assertEquals(Money.of(AED, "250.00"), journal.balance("A", 6, journal.lastSequence()));
    }

    @Test
    void conflictingIdsDoNotReplaceTheAcceptedEventOrBreakItsRetries() {
        var accepted = new Request("E1", 1, new Request.Postings(List.of(new Posting("A", 1, Money.of(AED, "10.00")))));
        var conflict = new Request("E1", 2, accepted.data());
        assertTrue(replay.process(1, accepted));
        var before = journal.entries();

        assertFalse(replay.process(2, conflict));
        assertFalse(replay.process(2, conflict));
        assertTrue(replay.process(2, accepted));
        assertEquals(before, journal.entries());
        assertEquals(List.of(new Rejection(2, conflict, "Conflicting event ID: E1"),
                new Rejection(2, conflict, "Conflicting event ID: E1")), replay.rejections());
        assertEquals(accepted, journal.find("E1", journal.lastSequence()).orElseThrow().request());
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void expectedDomainErrorsAreRecordedWithoutAnyPartialEntry(Request.Data data) {
        var request = new Request("INVALID", 1, data);
        assertFalse(replay.process(1, request));
        assertEquals(List.of(), journal.entries());
        assertEquals(Money.of(AED, "250.00"), journal.balance("A", 1, journal.lastSequence()));
        assertEquals(1, replay.rejections().size());
        assertSame(request, replay.rejections().getFirst().request());
        assertFalse(replay.rejections().getFirst().reason().isBlank());
    }

    static Stream<Request.Data> invalidRequests() {
        return Stream.of(
                new Request.Postings(List.of(new Posting("A", 1, Money.of(AED, "10.00")),
                        new Posting("UNKNOWN", 1, Money.of(AED, "10.00")))),
                new Request.Postings(List.of(new Posting("A", 1, Money.of(AED, "10.00")),
                        new Posting("A", 1, Money.of(BHD, "10.000")))),
                new Request.Hold("A", "AUTH", Money.of(BHD, "10.000")),
                new Request.Hold("A", " ", Money.of(AED, "10.00")),
                new Request.Hold("A", "AUTH", Money.of(AED, "0.00")),
                new Request.Settlement("UNKNOWN", "AUTH", Money.of(AED, "10.00"), 1),
                new Request.Settlement("A", "AUTH", Money.of(BHD, "10.000"), 1),
                new Request.Settlement("A", "AUTH", Money.of(AED, "10.00"), 0),
                new Request.Settlement("A", "AUTH", Money.of(AED, "-10.00"), 1),
                new Request.Reversal("A", "UNKNOWN", 1),
                new Request.FeeReassessment("A"),
                new Request.DayClose("UNKNOWN"),
                new Request.InterestCapitalization("A"));
    }

    @Test
    void authorizationDeclinesAndSettlementDiagnosticsAreAcceptedOutcomes() {
        var hold = new Request("HOLD", 1, new Request.Hold("A", "AUTH", Money.of(AED, "300.00")));
        assertTrue(replay.process(1, hold));
        assertTrue(replay.process(1, new Request("MISSING", 1,
                new Request.Settlement("A", "UNKNOWN", Money.of(AED, "180.00"), 1))));
        assertTrue(replay.process(1, new Request("DECLINED", 1,
                new Request.Settlement("A", "AUTH", Money.of(AED, "50.00"), 1))));

        assertEquals(List.of(), replay.rejections());
        assertEquals(List.of(Diagnostic.MISSING_AUTHORIZATION, Diagnostic.DECLINED_AUTHORIZATION),
                journal.entries().stream().flatMap(entry -> entry.diagnostics().stream()).toList());
        assertEquals(DECLINED, new Authorizations(journal).states(journal.lastSequence()).getFirst().status());
        assertTrue(replay.process(1, new Request("FUND", 1,
                new Request.Postings(List.of(new Posting("A", 1, Money.of(AED, "300.00")))))));
        var before = journal.entries();
        assertTrue(replay.process(1, hold));
        assertEquals(before, journal.entries());
        assertEquals(DECLINED, new Authorizations(journal).states(journal.lastSequence()).getFirst().status());
    }

    @Test
    void closeReassessmentAndCapitalizationUseTheSameRejectionBoundary() {
        assertFalse(replay.process(6, new Request("CAP", 6, new Request.InterestCapitalization("A"))));
        IntStream.rangeClosed(1, 6).forEach(day ->
                assertTrue(replay.process(day, new Request("C" + day, day, new Request.DayClose("A")))));
        assertTrue(replay.process(6, new Request("LATE", 5,
                new Request.Postings(List.of(new Posting("A", 5, Money.of(AED, "250.00")))))));
        assertFalse(replay.process(6, new Request("CAP", 6, new Request.InterestCapitalization("A"))));
        var reassessment = new Request("REFRESH", 6, new Request.FeeReassessment("A"));
        assertTrue(replay.process(6, reassessment));
        assertEquals(reassessment, journal.find("REFRESH", journal.lastSequence()).orElseThrow().request());

        // A submitted Day 5 must be rejected, not silently rewritten to Day 6.
        assertFalse(replay.process(6, new Request("CAP", 5, new Request.InterestCapitalization("A"))));
        assertTrue(replay.process(6, new Request("CAP", 6, new Request.InterestCapitalization("A"))));
        assertEquals(Money.of(AED, "500.80"), journal.balance("A", 6, journal.lastSequence()));
        assertFalse(replay.process(6, new Request("SECOND-CAP", 6, new Request.InterestCapitalization("A"))));
        assertFalse(replay.process(6, new Request("REOPEN", 6, new Request.FeeReassessment("A"))));
        assertEquals(5, replay.rejections().size());
    }

    @Test
    void nullInputsAndInvalidProcessingContextPropagateWithoutBecomingRejections() {
        var request = new Request("BROKEN", 1, new Request.Hold("A", "AUTH", null));
        assertThrows(NullPointerException.class, () -> replay.process(1, request));
        assertThrows(NullPointerException.class, () -> replay.process(1, null));
        assertThrows(IllegalArgumentException.class, () -> replay.process(0,
                new Request("C1", 1, new Request.DayClose("A"))));
        assertEquals(List.of(), journal.entries());
        assertEquals(List.of(), replay.rejections());
    }
}
