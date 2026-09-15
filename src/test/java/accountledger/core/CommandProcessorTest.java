package accountledger.core;

import accountledger.domain.Account;
import accountledger.domain.Authorization;
import accountledger.domain.Command;
import accountledger.domain.enums.Diagnostic;
import accountledger.domain.Money;
import accountledger.domain.Posting;
import accountledger.domain.Rejection;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static accountledger.domain.Authorization.Status.DECLINED;
import static accountledger.domain.enums.CurrencyUnit.AED;
import static accountledger.domain.enums.CurrencyUnit.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandProcessorTest {
    private final Journal journal = new Journal(new Account("A", Money.of(AED, "250.00")));
    private final CommandProcessor processor = new CommandProcessor(journal);

    @Test
    void aRejectedSettlementKeepsFundsAndHoldAndDoesNotReserveItsEventId() {
        assertTrue(processor.process(1, new Command("HOLD", 1, new Command.Hold("A", "AUTH", Money.of(AED, "200.00")))));
        var before = journal.entries();
        var invalid = new Command("SETTLE", 2, new Command.Settlement("A", "AUTH", Money.of(AED, "0.00"), 2));

        assertFalse(processor.process(2, invalid));
        var failures = processor.rejections();
        assertEquals(List.of(new Rejection(2, invalid,
                "Settlement requires an authorization ID and a positive amount")), failures);
        assertEquals(before, journal.entries());
        assertEquals(Money.of(AED, "250.00"), journal.balance("A", 2, journal.lastSequence()));
        assertEquals(Money.of(AED, "50.00"), new Authorizations(journal).available("A", 2, journal.lastSequence()));

        var corrected = new Command("SETTLE", 2, new Command.Settlement("A", "AUTH", Money.of(AED, "185.00"), 2));
        assertTrue(processor.process(2, corrected));
        assertEquals(Money.of(AED, "65.00"), new Authorizations(journal).available("A", 2, journal.lastSequence()));
        assertEquals(failures, processor.rejections());
    }

    @Test
    void aMissingAuthorizationRejectsSettlementUntilThatAuthorizationArrives() {
        var settlement = new Command("SETTLE", 1,
                new Command.Settlement("A", "AUTH", Money.of(AED, "185.00"), 1));
        assertFalse(processor.process(1, settlement));
        var failures = processor.rejections();
        assertEquals(List.of(new Rejection(1, settlement, "Unknown authorization: AUTH")), failures);
        assertEquals(List.of(), journal.entries());
        assertEquals(Money.of(AED, "250.00"), new Authorizations(journal).available("A", 1, journal.lastSequence()));

        assertTrue(processor.process(2, new Command("HOLD", 2,
                new Command.Hold("A", "AUTH", Money.of(AED, "200.00")))));
        assertTrue(processor.process(2, settlement));
        var accepted = journal.entries();
        assertTrue(processor.process(2, settlement));
        assertEquals(accepted, journal.entries());
        assertEquals(Money.of(AED, "65.00"), new Authorizations(journal).available("A", 2, journal.lastSequence()));
        assertEquals(failures, processor.rejections());
    }

    @Test
    void everyFailedAttemptIsRecordedAndTheSameRequestCanLaterSucceed() {
        var reversal = new Command("REVERSE", 5, new Command.Reversal("A", "ORIGINAL", 2));
        assertFalse(processor.process(5, reversal));
        var day5Failures = processor.rejections();
        assertFalse(processor.process(6, reversal));

        assertEquals(List.of(new Rejection(5, reversal, "Unknown original event: ORIGINAL")), day5Failures);
        assertEquals(new Rejection(6, reversal, "Unknown original event: ORIGINAL"), processor.rejections().getLast());
        assertSame(reversal, processor.rejections().getLast().command());
        assertEquals(List.of(), journal.entries());
        assertThrows(UnsupportedOperationException.class, () -> day5Failures.clear());

        assertTrue(processor.process(6, new Command("ORIGINAL", 6,
                new Command.Postings(List.of(new Posting("A", 2, Money.of(AED, "-10.00")))))));
        assertTrue(processor.process(6, reversal));
        var accepted = journal.entries();
        assertTrue(processor.process(6, reversal));
        assertEquals(accepted, journal.entries());
        assertEquals(2, processor.rejections().size());
        assertEquals(Money.of(AED, "250.00"), journal.balance("A", 6, journal.lastSequence()));
    }

    @Test
    void conflictingIdsDoNotReplaceTheAcceptedEventOrBreakItsRetries() {
        var accepted = new Command("E1", 1, new Command.Postings(List.of(new Posting("A", 1, Money.of(AED, "10.00")))));
        var conflict = new Command("E1", 2, accepted.data());
        assertTrue(processor.process(1, accepted));
        var before = journal.entries();

        assertFalse(processor.process(2, conflict));
        assertFalse(processor.process(2, conflict));
        assertTrue(processor.process(2, accepted));
        assertEquals(before, journal.entries());
        assertEquals(List.of(new Rejection(2, conflict, "Conflicting event ID: E1"),
                new Rejection(2, conflict, "Conflicting event ID: E1")), processor.rejections());
        assertEquals(accepted, journal.find("E1", journal.lastSequence()).orElseThrow().command());
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void expectedDomainErrorsAreRecordedWithoutAnyPartialEntry(Command.Data data) {
        var command = new Command("INVALID", 1, data);
        assertFalse(processor.process(1, command));
        assertEquals(List.of(), journal.entries());
        assertEquals(Money.of(AED, "250.00"), journal.balance("A", 1, journal.lastSequence()));
        assertEquals(1, processor.rejections().size());
        assertSame(command, processor.rejections().getFirst().command());
        assertFalse(processor.rejections().getFirst().reason().isBlank());
    }

    static Stream<Command.Data> invalidRequests() {
        return Stream.of(
                new Command.Postings(List.of(new Posting("A", 1, Money.of(AED, "10.00")),
                        new Posting("UNKNOWN", 1, Money.of(AED, "10.00")))),
                new Command.Postings(List.of(new Posting("A", 1, Money.of(AED, "10.00")),
                        new Posting("A", 1, Money.of(BHD, "10.000")))),
                new Command.Hold("A", "AUTH", Money.of(BHD, "10.000")),
                new Command.Hold("A", " ", Money.of(AED, "10.00")),
                new Command.Hold("A", "AUTH", Money.of(AED, "0.00")),
                new Command.Settlement("UNKNOWN", "AUTH", Money.of(AED, "10.00"), 1),
                new Command.Settlement("A", "AUTH", Money.of(BHD, "10.000"), 1),
                new Command.Settlement("A", "AUTH", Money.of(AED, "10.00"), 0),
                new Command.Settlement("A", "AUTH", Money.of(AED, "-10.00"), 1),
                new Command.Reversal("A", "UNKNOWN", 1),
                new Command.FeeReassessment("A"),
                new Command.DayClose("UNKNOWN"),
                new Command.InterestCapitalization("A"));
    }

    @Test
    void authorizationDeclinesAndSettlementDiagnosticsAreAcceptedOutcomes() {
        var hold = new Command("HOLD", 1, new Command.Hold("A", "AUTH", Money.of(AED, "300.00")));
        assertTrue(processor.process(1, hold));
        assertTrue(processor.process(1, new Command("DECLINED", 1,
                new Command.Settlement("A", "AUTH", Money.of(AED, "50.00"), 1))));

        assertEquals(List.of(), processor.rejections());
        assertEquals(List.of(Diagnostic.DECLINED_AUTHORIZATION),
                journal.entries().stream().flatMap(entry -> entry.diagnostics().stream()).toList());
        assertEquals(DECLINED, new Authorizations(journal).states(journal.lastSequence()).getFirst().status());
        assertTrue(processor.process(1, new Command("FUND", 1,
                new Command.Postings(List.of(new Posting("A", 1, Money.of(AED, "300.00")))))));
        var before = journal.entries();
        assertTrue(processor.process(1, hold));
        assertEquals(before, journal.entries());
        assertEquals(DECLINED, new Authorizations(journal).states(journal.lastSequence()).getFirst().status());
    }

    @Test
    void closeReassessmentAndCapitalizationUseTheSameRejectionBoundary() {
        assertFalse(processor.process(6, new Command("CAP", 6, new Command.InterestCapitalization("A"))));
        IntStream.rangeClosed(1, 6).forEach(day ->
                assertTrue(processor.process(day, new Command("C" + day, day, new Command.DayClose("A")))));
        assertTrue(processor.process(6, new Command("LATE", 5,
                new Command.Postings(List.of(new Posting("A", 5, Money.of(AED, "250.00")))))));
        assertFalse(processor.process(6, new Command("CAP", 6, new Command.InterestCapitalization("A"))));
        var reassessment = new Command("REFRESH", 6, new Command.FeeReassessment("A"));
        assertTrue(processor.process(6, reassessment));
        assertEquals(reassessment, journal.find("REFRESH", journal.lastSequence()).orElseThrow().command());

        // A submitted Day 5 must be rejected, not silently rewritten to Day 6.
        assertFalse(processor.process(6, new Command("CAP", 5, new Command.InterestCapitalization("A"))));
        assertTrue(processor.process(6, new Command("CAP", 6, new Command.InterestCapitalization("A"))));
        assertEquals(Money.of(AED, "500.80"), journal.balance("A", 6, journal.lastSequence()));
        assertFalse(processor.process(6, new Command("SECOND-CAP", 6, new Command.InterestCapitalization("A"))));
        assertFalse(processor.process(6, new Command("REOPEN", 6, new Command.FeeReassessment("A"))));
        assertEquals(5, processor.rejections().size());
    }

    @Test
    void nullInputsAndInvalidProcessingContextPropagateWithoutBecomingRejections() {
        var command = new Command("BROKEN", 1, new Command.Hold("A", "AUTH", null));
        assertThrows(NullPointerException.class, () -> processor.process(1, command));
        assertThrows(NullPointerException.class, () -> processor.process(1, null));
        assertThrows(IllegalArgumentException.class, () -> processor.process(0,
                new Command("C1", 1, new Command.DayClose("A"))));
        assertEquals(List.of(), journal.entries());
        assertEquals(List.of(), processor.rejections());
    }
}
