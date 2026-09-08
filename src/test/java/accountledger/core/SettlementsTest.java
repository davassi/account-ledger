package accountledger.core;

import accountledger.domain.CurrencyUnit;
import accountledger.domain.Money;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static accountledger.core.Authorization.Status.APPROVED;
import static accountledger.core.Authorization.Status.DECLINED;
import static accountledger.core.Authorization.Status.SETTLED;
import static accountledger.domain.CurrencyUnit.AED;
import static accountledger.domain.CurrencyUnit.BHD;
import static accountledger.core.Diagnostic.DECLINED_AUTHORIZATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementsTest {
    private final Journal journal = new Journal(
            new Account("ACC-001", Money.of(AED, "0.00")),
            new Account("ACC-002", Money.of(BHD, "0.000")));
    private final Authorizations authorizations = new Authorizations(journal);
    private final Settlements settlements = new Settlements(journal);
    private Authorization authA;

    @BeforeEach
    void replayThroughE4() {
        journal.append("E1", 1, List.of(new Posting("ACC-001", 1, Money.of(AED, "1200.00"))));
        journal.append("E2", 1, List.of(new Posting("ACC-001", 1, Money.of(AED, "-950.00"))));
        authA = authorizations.authorize("E3", 2, "ACC-001", "Auth-A", Money.of(AED, "200.00"));
        journal.append("E4", 3, List.of(new Posting("ACC-001", 3, Money.of(AED, "400.00"))));
    }

    @Test
    void authASettlesFor185AndReleasesAll200WithoutChangingTheOriginal() {
        long before = journal.lastSequence();
        var entry = settlements.settle("E5", 4, "ACC-001", "Auth-A", Money.of(AED, "185.00"), 4);
        var settled = new Authorization("ACC-001", "Auth-A", Money.of(AED, "200.00"), SETTLED);

        assertEquals(before + 1, entry.sequence());
        assertEquals(List.of(new Posting("ACC-001", 4, Money.of(AED, "-185.00"))), entry.postings());
        assertEquals(List.of(settled), entry.authorizations());
        assertEquals(List.of(), entry.diagnostics());
        assertEquals(Money.of(AED, "465.00"), journal.balance("ACC-001", 4, entry.sequence()));
        assertEquals(Money.of(AED, "465.00"), authorizations.available("ACC-001", 4, entry.sequence()));
        assertEquals(List.of(authA, settled), authorizations.decisions(entry.sequence()));
        assertEquals(List.of(settled), authorizations.states(entry.sequence()));
        assertEquals(List.of(authA), authorizations.states(before));
        assertEquals(APPROVED, authA.status());
        assertEquals(Money.of(AED, "50.00"), authorizations.available("ACC-001", 2, before));
    }

    @Test
    void unmatchedAuthZIsRejectedWithoutChangingBalancesOrHistory() {
        settlements.settle("E5", 4, "ACC-001", "Auth-A", Money.of(AED, "185.00"), 4);
        var before = journal.entries();

        var error = assertThrows(RequestRejectedException.class,
                () -> settlements.settle("E6", 4, "ACC-001", "Auth-Z", Money.of(AED, "180.00"), 4));

        assertEquals("Unknown authorization: Auth-Z", error.getMessage());
        assertEquals(before, journal.entries());
        assertEquals(Money.of(AED, "465.00"), journal.balance("ACC-001", 4, journal.lastSequence()));
        assertEquals(Money.of(AED, "465.00"), authorizations.available("ACC-001", 4, journal.lastSequence()));
    }

    @Test
    void aDeclinedReferencePostsWithAnAnomalyAndDoesNotReleaseOtherHolds() {
        var declined = authorizations.authorize("DECLINE", 4, "ACC-001", "Declined", Money.of(AED, "620.00"));
        var entry = settlements.settle("CAPTURE", 4, "ACC-001", "Declined", Money.of(AED, "180.00"), 4);

        assertEquals(DECLINED, declined.status());
        assertEquals(List.of(DECLINED_AUTHORIZATION), entry.diagnostics());
        assertEquals(List.of(authA, declined), authorizations.states(entry.sequence()));
        assertEquals(Money.of(AED, "470.00"), journal.balance("ACC-001", 4, entry.sequence()));
        assertEquals(Money.of(AED, "270.00"), authorizations.available("ACC-001", 4, entry.sequence()));
    }

    @Test
    void settlementCanExceedTheHoldAndDriveTheLedgerNegative() {
        var entry = settlements.settle("LARGE", 4, "ACC-001", "Auth-A", Money.of(AED, "650.01"), 4);

        assertEquals(SETTLED, authorizations.states(entry.sequence()).getFirst().status());
        assertEquals(Money.of(AED, "-0.01"), authorizations.available("ACC-001", 4, entry.sequence()));
    }

    @ParameterizedTest
    @CsvSource({"2, 465.00", "6, 650.00"})
    void valueDayAffectsTheDebitWhileTheHoldIsReleasedAtRecording(int valueDay, String balanceAtDay4) {
        long before = journal.lastSequence();
        var entry = settlements.settle("DATED", 4, "ACC-001", "Auth-A", Money.of(AED, "185.00"), valueDay);

        assertEquals(Money.of(AED, balanceAtDay4), authorizations.available("ACC-001", 4, entry.sequence()));
        assertEquals(Money.of(AED, "465.00"), authorizations.available("ACC-001", 6, entry.sequence()));
        assertEquals(Money.of(AED, "50.00"), authorizations.available("ACC-001", 2, before));
        assertEquals(List.of(authA), authorizations.states(before));
    }

    @Test
    void settledHoldsStayReleasedAfterBackdatingAndRetries() {
        var entry = settlements.settle("E5", 4, "ACC-001", "Auth-A", Money.of(AED, "185.00"), 4);
        journal.append("E7", 5, List.of(new Posting("ACC-001", 2, Money.of(AED, "-620.00"))));
        long cutoff = journal.lastSequence();
        var rebuilt = new Authorizations(journal);

        assertSame(entry, new Settlements(journal).settle("E5", 4, "ACC-001", "Auth-A", Money.of(AED, "185.00"), 4));
        assertSame(authA, rebuilt.authorize("E3", 2, "ACC-001", "Auth-A", Money.of(AED, "200.00")));
        assertEquals(cutoff, journal.lastSequence());
        assertEquals(SETTLED, rebuilt.states(cutoff).getFirst().status());
        assertEquals(Money.of(AED, "-155.00"), rebuilt.available("ACC-001", 5, cutoff)); // Before fees.
        assertEquals(Money.of(AED, "465.00"), rebuilt.available("ACC-001", 4, entry.sequence()));
    }

    @Test
    void referencesAndHoldReleaseAreScopedToTheAccount() {
        journal.append("BHD-FUND", 3, List.of(new Posting("ACC-002", 3, Money.of(BHD, "10.000"))));
        var before = journal.entries();
        assertThrows(RequestRejectedException.class,
                () -> settlements.settle("BHD-SETTLE", 4, "ACC-002", "Auth-A", Money.of(BHD, "3.334"), 4));
        assertEquals(before, journal.entries());

        authorizations.authorize("BHD-HOLD", 3, "ACC-002", "Auth-A", Money.of(BHD, "10.000"));
        var entry = settlements.settle("BHD-SETTLE", 4, "ACC-002", "Auth-A", Money.of(BHD, "3.334"), 4);

        assertEquals(Money.of(BHD, "6.666"), authorizations.available("ACC-002", 4, entry.sequence()));
        assertEquals(Money.of(AED, "450.00"), authorizations.available("ACC-001", 4, entry.sequence()));
        settlements.settle("E5", 4, "ACC-001", "Auth-A", Money.of(AED, "185.00"), 4);
        assertEquals(List.of(SETTLED, SETTLED), authorizations.states(journal.lastSequence()).stream()
                .map(Authorization::status).toList());
    }

    @ParameterizedTest
    @CsvSource({"200.00, APPROVED", "620.00, DECLINED"})
    void aReferenceCannotBeSettledTwiceOrReusedForANewHold(String amount, Authorization.Status status) {
        var authId = "Another";
        assertEquals(status, authorizations.authorize("HOLD", 4, "ACC-001", authId, Money.of(AED, amount)).status());
        var entry = settlements.settle("FIRST", 4, "ACC-001", authId, Money.of(AED, "185.00"), 4);
        var before = journal.entries();

        assertThrows(IllegalArgumentException.class,
                () -> settlements.settle("SECOND", 5, "ACC-001", authId, Money.of(AED, "185.00"), 5));
        assertThrows(IllegalArgumentException.class,
                () -> authorizations.authorize("NEW-HOLD", 5, "ACC-001", authId, Money.of(AED, "90.00")));
        assertSame(entry, settlements.settle("FIRST", 4, "ACC-001", authId, Money.of(AED, "185.00"), 4));
        assertEquals(before, journal.entries());
    }

    @ParameterizedTest
    @CsvSource({"5, 4, 185.00", "4, 2, 185.00", "4, 4, 185.01"})
    void changedDatesOrAmountAreNotRetries(int bookedDay, int valueDay, String amount) {
        settlements.settle("E5", 4, "ACC-001", "Auth-A", Money.of(AED, "185.00"), 4);
        var before = journal.entries();

        assertThrows(IllegalArgumentException.class,
                () -> settlements.settle("E5", bookedDay, "ACC-001", "Auth-A", Money.of(AED, amount), valueDay));
        assertEquals(before, journal.entries());
    }

    @ParameterizedTest
    @CsvSource({"ACC-001, AED, 0.00, 4", "ACC-001, AED, -185.00, 4", "ACC-001, BHD, 185.00, 4",
            "UNKNOWN, AED, 185.00, 4", "ACC-001, AED, 185.00, 0"})
    void invalidSettlementsLeaveBothTheLedgerAndTheHoldUntouched(String accountId, CurrencyUnit currency,
                                                               String amount, int valueDay) {
        var before = journal.entries();
        assertThrows(IllegalArgumentException.class,
                () -> settlements.settle("INVALID", 4, accountId, "Auth-A", Money.of(currency, amount), valueDay));

        assertEquals(before, journal.entries());
        assertEquals(List.of(authA), authorizations.states(journal.lastSequence()));
        assertEquals(Money.of(AED, "450.00"), authorizations.available("ACC-001", 4, journal.lastSequence()));
    }

    @Test
    void diagnosticCollectionsAreCopiedBeforeRecording() {
        var diagnostics = new ArrayList<>(List.of(DECLINED_AUTHORIZATION));
        var effects = new Journal.Effects(List.of(), List.of(), diagnostics);
        diagnostics.clear();

        assertEquals(List.of(DECLINED_AUTHORIZATION), effects.diagnostics());
        assertThrows(UnsupportedOperationException.class, () -> effects.diagnostics().clear());
        assertThrows(UnsupportedOperationException.class, () -> authorizations.states(journal.lastSequence()).clear());
        assertEquals(List.of(), authorizations.states(0));
        assertThrows(IllegalArgumentException.class, () -> authorizations.states(journal.lastSequence() + 1));
    }
}
