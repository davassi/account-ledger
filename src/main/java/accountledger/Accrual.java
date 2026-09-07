package accountledger;

import java.util.Objects;

/** Rounded interest for one value day; a fact, not a spendable posting. */
public record Accrual(String accountId, int day, Money amount) {
    public Accrual {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        if (accountId.isBlank() || day < 1 || amount.signum() < 0) {
            throw new IllegalArgumentException("Accrual requires an account, a positive day and a non-negative amount");
        }
    }
}
