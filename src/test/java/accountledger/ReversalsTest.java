package accountledger;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static accountledger.Authorization.Status.DECLINED;
import static accountledger.CurrencyUnit.AED;
import static accountledger.CurrencyUnit.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReversalsTest {
    private final Journal journal = new Journal(
            new Account("ACC-001", Money.of(AED, "0.00")),
            new Account("ACC-002", Money.of(BHD, "0.000")));
    private final Authorizations authorizations = new Authorizations(journal);
    private final Reversals reversals = new Reversals(journal);
    private Journal.Entry debit;

    @BeforeEach
    void replayThroughE7WithoutFees() {
        journal.append("E1", 1, List.of(aed(1, "1200.00")));
        journal.append("E2", 1, List.of(aed(1, "-950.00")));
        authorizations.authorize("E3", 2, "ACC-001", "Auth-A", Money.of(AED, "200.00"));
        journal.append("E4", 3, List.of(aed(3, "400.00")));
        var settlements = new Settlements(journal);
        settlements.settle("E5", 4, "ACC-001", "Auth-A", Money.of(AED, "185.00"), 4);
        settlements.settle("E6", 4, "ACC-001", "Auth-Z", Money.of(AED, "180.00"), 4);
        debit = journal.append("E7", 5, List.of(aed(2, "-620.00")));
    }

    @Test
    void e9CompensatesE7WithoutChangingHistoryOrReevaluatingAuthB() {
        var authB = authorizations.authorize("E8", 5, "ACC-001", "Auth-B", Money.of(AED, "90.00"));
        var before = journal.entries();
        var states = authorizations.states(journal.lastSequence());
        var reversal = reversals.reverse("E9", 6, "ACC-001", "E7", 2);

        assertEquals(before.size() + 1, reversal.sequence());
        assertEquals(new Request.Reversal("ACC-001", "E7", 2), reversal.request().data());
        assertEquals(List.of(aed(2, "620.00")), reversal.postings());
        assertEquals(List.of(), reversal.authorizations());
        assertEquals(List.of(), reversal.diagnostics());
        assertEquals(before, journal.entries(before.size()));
        assertSame(debit, journal.find("E7", reversal.sequence()).orElseThrow());
        assertEquals(List.of(aed(2, "-620.00")), debit.postings());
        assertEquals(Money.of(AED, "-370.00"), journal.balance("ACC-001", 2, debit.sequence()));
        assertEquals(Money.of(AED, "250.00"), journal.balance("ACC-001", 2, reversal.sequence()));
        assertEquals(Money.of(AED, "285.00"), authorizations.available("ACC-001", 6, reversal.sequence()));
        assertEquals(DECLINED, authB.status());
        assertEquals(states, authorizations.states(reversal.sequence()));
        assertTrue(journal.find("E9", before.size()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> journal.find("E9", reversal.sequence() + 1));
    }

    @Test
    void aSeparatelyBookedFeeIsNotRefunded() {
        // A synthetic fee isolates reversal behavior; daily assessment is not implemented yet.
        var fee = journal.append("FEE-D2", 5, List.of(aed(2, "-25.00")));
        var reversal = reversals.reverse("E9", 6, "ACC-001", "E7", 2);

        assertEquals(fee.sequence() + 1, reversal.sequence());
        assertSame(fee, journal.find("FEE-D2", reversal.sequence()).orElseThrow());
        assertEquals(List.of(aed(2, "620.00")), reversal.postings());
        assertEquals(Money.of(AED, "225.00"), journal.balance("ACC-001", 2, reversal.sequence()));
        assertEquals(Money.of(AED, "260.00"), journal.balance("ACC-001", 6, reversal.sequence()));
    }

    @Test
    void reversesEveryBhdInstallmentInOneEntryWithoutAffectingAed() {
        var installments = Money.of(BHD, "10.000").split(3).stream()
                .map(amount -> new Posting("ACC-002", 5, amount)).toList();
        var original = journal.append("E10", 5, installments);
        var reversal = reversals.reverse("BHD-REVERSAL", 6, "ACC-002", "E10", 5);

        assertEquals(original.sequence() + 1, reversal.sequence());
        assertEquals(List.of(Money.of(BHD, "-3.334"), Money.of(BHD, "-3.333"), Money.of(BHD, "-3.333")),
                reversal.postings().stream().map(Posting::delta).toList());
        assertEquals(installments, original.postings());
        assertEquals(Money.of(BHD, "10.000"), journal.balance("ACC-002", 5, original.sequence()));
        assertEquals(Money.of(BHD, "0.000"), journal.balance("ACC-002", 5, reversal.sequence()));
        assertEquals(Money.of(AED, "-335.00"), journal.balance("ACC-001", 6, reversal.sequence()));
    }

    @ParameterizedTest
    @CsvSource({"E5, 185.00, -150.00", "E6, 180.00, -155.00"})
    void aSettlementRefundDoesNotReopenHoldsOrEraseDiagnostics(String reference, String amount, String balance) {
        var states = authorizations.states(journal.lastSequence());
        var original = journal.find(reference, journal.lastSequence()).orElseThrow();
        var reversal = reversals.reverse("REFUND", 6, "ACC-001", reference, 4);

        assertEquals(List.of(aed(4, amount)), reversal.postings());
        assertEquals(Money.of(AED, balance), authorizations.available("ACC-001", 6, reversal.sequence()));
        assertEquals(states, authorizations.states(reversal.sequence()));
        assertSame(original, journal.find(reference, reversal.sequence()).orElseThrow());
    }

    @Test
    void theRequestedValueDayControlsWhenTheCompensationTakesEffect() {
        var reversal = reversals.reverse("LATE-REVERSAL", 6, "ACC-001", "E7", 6);

        assertEquals(Money.of(AED, "-370.00"), journal.balance("ACC-001", 2, reversal.sequence()));
        assertEquals(Money.of(AED, "-335.00"), journal.balance("ACC-001", 5, reversal.sequence()));
        assertEquals(Money.of(AED, "285.00"), journal.balance("ACC-001", 6, reversal.sequence()));
    }

    @Test
    void retriesReturnOriginalEntriesEvenAfterLaterPostings() {
        var reversal = reversals.reverse("E9", 6, "ACC-001", "E7", 2);
        journal.append("LATER", 6, List.of(aed(6, "400.00")));
        var before = journal.entries();

        assertSame(reversal, new Reversals(journal).reverse("E9", 6, "ACC-001", "E7", 2));
        assertSame(debit, journal.append("E7", 5, List.of(aed(2, "-620.00"))));
        assertEquals(before, journal.entries());
    }

    @Test
    void aSecondReversalOrAReversalOfAReversalIsRejected() {
        reversals.reverse("E9", 6, "ACC-001", "E7", 2);
        var before = journal.entries();
        var rebuilt = new Reversals(journal);

        List.of("E7", "E9").forEach(reference -> assertThrows(IllegalArgumentException.class,
                () -> rebuilt.reverse("SECOND", 6, "ACC-001", reference, 2)));
        assertEquals(before, journal.entries());
    }

    @ParameterizedTest
    @CsvSource({"UNKNOWN, ACC-001", "E3, ACC-001", "E7, ACC-002", "E7, UNKNOWN", "E9, ACC-001"})
    void invalidReferencesOrAccountsCannotCreateACompensation(String reference, String accountId) {
        var before = journal.entries();

        assertThrows(IllegalArgumentException.class, () -> reversals.reverse("E9", 6, accountId, reference, 2));
        assertEquals(before, journal.entries());
    }

    @Test
    void emptyOrMultiAccountEntriesCannotBePartiallyReversed() {
        journal.append("EMPTY", 5, List.of());
        journal.append("MIXED", 5, List.of(aed(5, "10.00"), new Posting("ACC-002", 5, Money.of(BHD, "10.000"))));
        var before = journal.entries();

        List.of("EMPTY", "MIXED").forEach(reference -> assertThrows(IllegalArgumentException.class,
                () -> reversals.reverse("INVALID", 6, "ACC-001", reference, 5)));
        assertEquals(before, journal.entries());
    }

    @ParameterizedTest
    @CsvSource({"5, ACC-001, E7, 2", "6, ACC-001, E7, 3", "6, ACC-002, E7, 2", "6, ACC-001, E4, 2"})
    void changedReversalInputsConflictWithTheOriginalRequest(int bookedDay, String accountId, String reference, int valueDay) {
        reversals.reverse("E9", 6, "ACC-001", "E7", 2);
        var before = journal.entries();

        assertThrows(IllegalArgumentException.class, () -> reversals.reverse("E9", bookedDay, accountId, reference, valueDay));
        assertEquals(before, journal.entries());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void anInvalidValueDayDoesNotConsumeTheEventIdOrReference(int valueDay) {
        var before = journal.entries();
        assertThrows(IllegalArgumentException.class, () -> reversals.reverse("E9", 6, "ACC-001", "E7", valueDay));
        assertEquals(before, journal.entries());

        var reversal = reversals.reverse("E9", 6, "ACC-001", "E7", 2);
        assertEquals(before.size() + 1, reversal.sequence());
        assertEquals(Money.of(AED, "285.00"), journal.balance("ACC-001", 6, reversal.sequence()));
    }

    private static Posting aed(int valueDay, String amount) {
        return new Posting("ACC-001", valueDay, Money.of(AED, amount));
    }
}
