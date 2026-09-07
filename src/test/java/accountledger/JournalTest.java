package accountledger;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static accountledger.CurrencyUnit.AED;
import static accountledger.CurrencyUnit.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JournalTest {
    private final Journal journal = new Journal(
            new Account("ACC-001", Money.of(AED, "0.00")),
            new Account("ACC-002", Money.of(BHD, "0.000")));

    @Test
    void openingBalancesKeepTheirCurrencyAndAreIncludedOnce() {
        assertEquals(Money.of(AED, "0.00"), journal.balance("ACC-001", 1, 0));
        assertEquals(Money.of(BHD, "0.000"), journal.balance("ACC-002", 1, 0));

        var withOpening = new Journal(new Account("ACC-001", Money.of(AED, "1200.00")));
        var debit = withOpening.append("E2", 1, List.of(aed(1, "-950.00")));

        assertEquals(Money.of(AED, "1200.00"), withOpening.balance("ACC-001", 1, 0));
        assertEquals(Money.of(AED, "250.00"), withOpening.balance("ACC-001", 1, debit.sequence()));
    }

    @Test
    void backdatingAndCompensationPreserveEarlierKnowledge() {
        journal.append("E1", 1, List.of(aed(1, "1200.00")));
        journal.append("E2", 1, List.of(aed(1, "-950.00")));
        var before = journal.append("E4", 3, List.of(aed(3, "400.00")));
        var backdated = journal.append("E7", 5, List.of(aed(2, "-620.00")));
        var compensation = journal.append("E9", 6, List.of(aed(2, "620.00")));

        assertEquals(Money.of(AED, "250.00"), journal.balance("ACC-001", 2, before.sequence()));
        assertEquals(Money.of(AED, "-370.00"), journal.balance("ACC-001", 2, backdated.sequence()));
        assertEquals(Money.of(AED, "250.00"), journal.balance("ACC-001", 2, compensation.sequence()));
        assertEquals(Money.of(AED, "250.00"), journal.balance("ACC-001", 1, compensation.sequence()));
        assertEquals(Money.of(AED, "30.00"), journal.balance("ACC-001", 3, backdated.sequence()));
        assertEquals(Money.of(AED, "650.00"), journal.balance("ACC-001", 3, compensation.sequence()));
    }

    @Test
    void futureValueDatesDoNotBecomeEffectiveAtBooking() {
        var future = journal.append("FUTURE", 1, List.of(aed(3, "400.00")));

        assertEquals(Money.of(AED, "0.00"), journal.balance("ACC-001", 2, future.sequence()));
        assertEquals(Money.of(AED, "400.00"), journal.balance("ACC-001", 3, future.sequence()));
    }

    @Test
    void lateInstallmentsAreRecordedTogetherInArrivalOrder() {
        journal.append("E7", 5, List.of(aed(2, "-620.00")));
        var before = journal.append("E9", 6, List.of(aed(2, "620.00")));
        var installments = Money.of(BHD, "10.000").split(3).stream()
                .map(amount -> new Posting("ACC-002", 5, amount)).toList();
        var late = journal.append("E10", 5, installments);

        assertEquals(before.sequence() + 1, late.sequence());
        assertEquals(3, late.postings().size());
        assertEquals(List.of("E7", "E9", "E10"), journal.entries().stream().map(Journal.Entry::eventId).toList());
        assertEquals(List.of(5, 6, 5), journal.entries().stream().map(Journal.Entry::bookedDay).toList());
        assertEquals(Money.of(BHD, "0.000"), journal.balance("ACC-002", 5, before.sequence()));
        assertEquals(Money.of(BHD, "10.000"), journal.balance("ACC-002", 5, late.sequence()));
        assertEquals(Money.of(AED, "0.00"), journal.balance("ACC-001", 5, late.sequence()));
    }

    @Test
    void identicalRetriesHaveNoSecondFinancialEffect() {
        var original = journal.append("E1", 1, List.of(aed(1, "1200.00")));
        var retry = journal.append("E1", 1, List.of(aed(1, "1200.00")));

        assertEquals(original, retry);
        assertEquals(List.of(original), journal.entries());
        assertEquals(Money.of(AED, "1200.00"), journal.balance("ACC-001", 1, journal.lastSequence()));
    }

    @Test
    void conflictingIdsLeaveTheOriginalUntouched() {
        var original = journal.append("E1", 1, List.of(aed(1, "1200.00")));

        assertThrows(IllegalArgumentException.class,
                () -> journal.append("E1", 2, original.postings()));
        assertThrows(IllegalArgumentException.class,
                () -> journal.append("E1", 1, List.of(aed(1, "950.00"))));
        assertEquals(List.of(original), journal.entries());
    }

    @ParameterizedTest
    @CsvSource({"UNKNOWN, AED", "ACC-001, BHD"})
    void anInvalidPostingRejectsTheWholeEntry(String accountId, CurrencyUnit currency) {
        var original = journal.append("E1", 1, List.of(aed(1, "1200.00")));
        var invalid = new Posting(accountId, 1, Money.of(currency, "10"));

        assertThrows(IllegalArgumentException.class,
                () -> journal.append("E2", 1, List.of(aed(1, "-950.00"), invalid)));
        assertEquals(List.of(original), journal.entries());
        assertEquals(Money.of(AED, "1200.00"), journal.balance("ACC-001", 1, journal.lastSequence()));

        var accepted = journal.append("E2", 1, List.of(aed(1, "-950.00")));
        assertEquals(original.sequence() + 1, accepted.sequence());
        assertEquals(Money.of(AED, "250.00"), journal.balance("ACC-001", 1, accepted.sequence()));
    }

    @Test
    void callersCannotRewriteHistoryThroughTheirCollections() {
        var accounts = new Account[]{new Account("ACC-001", Money.of(AED, "0.00"))};
        var isolated = new Journal(accounts);
        accounts[0] = new Account("ACC-001", Money.of(AED, "1200.00"));
        var postings = new ArrayList<>(List.of(aed(1, "1200.00")));
        var first = isolated.append("E1", 1, postings);
        var snapshot = isolated.entries();
        postings.clear();

        assertEquals(List.of(aed(1, "1200.00")), first.postings());
        assertThrows(UnsupportedOperationException.class, () -> first.postings().clear());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);

        isolated.append("E2", 1, List.of(aed(1, "-950.00")));
        assertEquals(List.of(first), snapshot);
        assertEquals(Money.of(AED, "250.00"), isolated.balance("ACC-001", 1, isolated.lastSequence()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void daysMustBePositive(int day) {
        assertThrows(IllegalArgumentException.class, () -> aed(day, "1200.00"));
        assertThrows(IllegalArgumentException.class, () -> journal.append("E1", day, List.of(aed(1, "1200.00"))));
        assertThrows(IllegalArgumentException.class, () -> journal.balance("ACC-001", day, 0));
        assertEquals(List.of(), journal.entries());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 1})
    void theKnowledgeCutoffMustReferToAnExistingPrefix(long sequence) {
        assertThrows(IllegalArgumentException.class, () -> journal.balance("ACC-001", 1, sequence));
    }

    @Test
    void unknownAccountsAndDuplicateDefinitionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> journal.balance("UNKNOWN", 1, 0));
        var account = new Account("ACC-001", Money.of(AED, "0.00"));
        assertThrows(IllegalStateException.class, () -> new Journal(account, account));
    }

    @Test
    void identifiersMustNotBeBlank() {
        assertThrows(IllegalArgumentException.class, () -> new Account(" ", Money.of(AED, "0.00")));
        assertThrows(IllegalArgumentException.class, () -> new Posting(" ", 1, Money.of(AED, "0.00")));
        assertThrows(IllegalArgumentException.class, () -> journal.append(" ", 1, List.of()));
    }

    @Test
    void anEmptyEntryAdvancesKnowledgeWithoutMovingFunds() {
        var entry = journal.append("NO-POSTINGS", 2, List.of());

        assertEquals(List.of(entry), journal.entries());
        assertEquals(1, journal.lastSequence());
        assertEquals(Money.of(AED, "0.00"), journal.balance("ACC-001", 2, entry.sequence()));
    }

    private Posting aed(int day, String amount) {
        return new Posting("ACC-001", day, Money.of(AED, amount));
    }
}
