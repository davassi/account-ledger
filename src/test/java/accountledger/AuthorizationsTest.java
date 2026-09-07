package accountledger;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static accountledger.Authorization.Status.APPROVED;
import static accountledger.Authorization.Status.DECLINED;
import static accountledger.CurrencyUnit.AED;
import static accountledger.CurrencyUnit.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthorizationsTest {
    private final Journal journal = new Journal(
            new Account("ACC-001", Money.of(AED, "0.00")),
            new Account("ACC-002", Money.of(BHD, "0.000")));
    private final Authorizations authorizations = new Authorizations(journal);

    @BeforeEach
    void fundAccount() {
        journal.append("E1", 1, List.of(aed(1, "1200.00")));
        journal.append("E2", 1, List.of(aed(1, "-950.00")));
    }

    @Test
    void authAReservesFundsWithoutChangingTheLedgerBalance() {
        var decision = authorizations.authorize("E3", 2, "ACC-001", "Auth-A", Money.of(AED, "200.00"));

        assertEquals(APPROVED, decision.status());
        assertEquals(Money.of(AED, "250.00"), journal.balance("ACC-001", 2, journal.lastSequence()));
        assertEquals(Money.of(AED, "50.00"), authorizations.available("ACC-001", 2));
        assertEquals(List.of(), journal.entries().getLast().postings());
        assertEquals(List.of(decision), journal.entries().getLast().authorizations());
    }

    @Test
    void holdsAccumulateAndExactlyZeroRemainingIsApproved() {
        authorizations.authorize("H1", 2, "ACC-001", "A", Money.of(AED, "200.00"));
        var exact = authorizations.authorize("H2", 2, "ACC-001", "B", Money.of(AED, "50.00"));
        var excess = authorizations.authorize("H3", 2, "ACC-001", "C", Money.of(AED, "0.01"));

        assertEquals(APPROVED, exact.status());
        assertEquals(DECLINED, excess.status());
        assertEquals(Money.of(AED, "0.00"), authorizations.available("ACC-001", 2));
        assertEquals(List.of(APPROVED, APPROVED, DECLINED),
                authorizations.decisions().stream().map(Authorization::status).toList());
    }

    @Test
    void backdatingAndCompensationDoNotRewriteDecisionsOrReevaluateRetries() {
        var authA = authorizations.authorize("E3", 2, "ACC-001", "Auth-A", Money.of(AED, "200.00"));
        journal.append("E4", 3, List.of(aed(3, "400.00")));
        journal.append("E7", 5, List.of(aed(2, "-620.00")));

        // This fixture isolates authorizations: no settlements or fees have been applied.
        assertEquals(Money.of(AED, "-170.00"), authorizations.available("ACC-001", 5));
        long beforeRetry = journal.lastSequence();
        assertSame(authA, authorizations.authorize("E3", 2, "ACC-001", "Auth-A", Money.of(AED, "200.00")));
        assertEquals(beforeRetry, journal.lastSequence());

        var authB = authorizations.authorize("E8", 5, "ACC-001", "Auth-B", Money.of(AED, "90.00"));
        assertEquals(DECLINED, authB.status());
        journal.append("E9", 6, List.of(aed(2, "620.00")));
        long afterCompensation = journal.lastSequence();

        var rebuilt = new Authorizations(journal);
        assertSame(authB, rebuilt.authorize("E8", 5, "ACC-001", "Auth-B", Money.of(AED, "90.00")));
        assertEquals(afterCompensation, journal.lastSequence());
        assertEquals(List.of(authA, authB), rebuilt.decisions());
        assertEquals(Money.of(AED, "450.00"), rebuilt.available("ACC-001", 6));
    }

    @Test
    void futureValueCreditsCannotFundAnAuthorizationToday() {
        journal.append("FUTURE", 2, List.of(aed(3, "400.00")));
        var decision = authorizations.authorize("H1", 2, "ACC-001", "A", Money.of(AED, "400.00"));

        assertEquals(DECLINED, decision.status());
        assertEquals(Money.of(AED, "250.00"), authorizations.available("ACC-001", 2));
        assertEquals(Money.of(AED, "650.00"), authorizations.available("ACC-001", 3));
        assertEquals(DECLINED, authorizations.decisions().getFirst().status());
    }

    @Test
    void holdsAndAuthorizationIdsBelongToTheirAccountAndCurrency() {
        authorizations.authorize("H1", 2, "ACC-001", "A", Money.of(AED, "200.00"));
        journal.append("BHD-FUND", 5, List.of(new Posting("ACC-002", 5, Money.of(BHD, "10.000"))));
        var exact = authorizations.authorize("H2", 5, "ACC-002", "A", Money.of(BHD, "10.000"));
        var excess = authorizations.authorize("H3", 5, "ACC-002", "B", Money.of(BHD, "0.001"));

        assertEquals(APPROVED, exact.status());
        assertEquals(DECLINED, excess.status());
        assertEquals(Money.of(BHD, "0.000"), authorizations.available("ACC-002", 5));
        assertEquals(Money.of(AED, "50.00"), authorizations.available("ACC-001", 5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"200.00", "400.00"})
    void aNewEventCannotReuseAnApprovedOrDeclinedAuthorizationId(String amount) {
        authorizations.authorize("H1", 2, "ACC-001", "A", Money.of(AED, amount));
        var before = journal.entries();

        assertThrows(IllegalArgumentException.class,
                () -> authorizations.authorize("H2", 2, "ACC-001", "A", Money.of(AED, amount)));
        assertEquals(before, journal.entries());
    }

    @ParameterizedTest
    @CsvSource({
            "3, ACC-001, A, 200.00, AED",
            "2, ACC-002, A, 200.00, AED",
            "2, ACC-001, B, 200.00, AED",
            "2, ACC-001, A, 200.01, AED",
            "2, ACC-001, A, 200.00, BHD"
    })
    void aRetryMustMatchEveryRequestField(int day, String accountId, String authId, String amount, CurrencyUnit currency) {
        authorizations.authorize("H1", 2, "ACC-001", "A", Money.of(AED, "200.00"));
        var before = journal.entries();

        assertThrows(IllegalArgumentException.class,
                () -> authorizations.authorize("H1", day, accountId, authId, Money.of(currency, amount)));
        assertEquals(before, journal.entries());
    }

    @ParameterizedTest
    @CsvSource({"UNKNOWN, AED, 200.00", "ACC-001, BHD, 200.00", "ACC-001, AED, 0.00", "ACC-001, AED, -200.00"})
    void invalidRequestsLeaveNoDecisionOrPosting(String accountId, CurrencyUnit currency, String amount) {
        var before = journal.entries();

        assertThrows(IllegalArgumentException.class,
                () -> authorizations.authorize("H1", 2, accountId, "A", Money.of(currency, amount)));
        assertEquals(before, journal.entries());
        assertEquals(List.of(), authorizations.decisions());
    }

    @Test
    void invalidIdentifiersAndDaysDoNotConsumeASequence() {
        var amount = Money.of(AED, "200.00");
        var before = journal.entries();

        assertThrows(IllegalArgumentException.class, () -> authorizations.authorize(" ", 2, "ACC-001", "A", amount));
        assertThrows(IllegalArgumentException.class, () -> authorizations.authorize("H1", 2, "ACC-001", " ", amount));
        assertThrows(IllegalArgumentException.class, () -> authorizations.authorize("H1", 0, "ACC-001", "A", amount));
        assertEquals(before, journal.entries());
    }

    @Test
    void anEventIdCannotBeSharedWithAPostingOrAnEmptyEntry() {
        var before = journal.entries();
        assertThrows(IllegalArgumentException.class,
                () -> authorizations.authorize("E1", 1, "ACC-001", "A", Money.of(AED, "200.00")));
        assertEquals(before, journal.entries());

        authorizations.authorize("H1", 2, "ACC-001", "A", Money.of(AED, "200.00"));
        var withHold = journal.entries();
        assertThrows(IllegalArgumentException.class, () -> journal.append("H1", 2, List.of()));
        assertEquals(withHold, journal.entries());
    }

    @ParameterizedTest
    @CsvSource({"UNKNOWN, AED", "ACC-001, BHD"})
    void anInvalidDecisionRejectsTheWholeJournalEntry(String accountId, CurrencyUnit currency) {
        var invalid = new Authorization(accountId, "A", Money.of(currency, "200.00"), APPROVED);
        var before = journal.entries();

        assertThrows(IllegalArgumentException.class,
                () -> journal.append("MIXED", 2, List.of(aed(2, "400.00")), List.of(invalid)));
        assertEquals(before, journal.entries());
    }

    @Test
    void decisionCollectionsCannotBeUsedToRewriteHistory() {
        var authA = new Authorization("ACC-001", "A", Money.of(AED, "200.00"), APPROVED);
        var input = new ArrayList<>(List.of(authA));
        var entry = journal.append("H1", 2, List.of(), input);
        var snapshot = authorizations.decisions();
        input.clear();

        assertEquals(List.of(authA), entry.authorizations());
        assertThrows(UnsupportedOperationException.class, () -> entry.authorizations().clear());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertSame(entry, journal.append("H1", 2, List.of(), List.of(authA)));
        assertThrows(IllegalArgumentException.class, () -> journal.append("H1", 2, List.of(),
                List.of(new Authorization("ACC-001", "A", Money.of(AED, "200.00"), DECLINED))));

        authorizations.authorize("H2", 2, "ACC-001", "B", Money.of(AED, "90.00"));
        assertEquals(List.of(authA), snapshot);
        assertEquals(APPROVED, entry.authorizations().getFirst().status());
    }

    private Posting aed(int day, String amount) {
        return new Posting("ACC-001", day, Money.of(AED, amount));
    }
}
