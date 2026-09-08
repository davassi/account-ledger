package accountledger.core;

import accountledger.domain.Money;

import java.util.Objects;

/**
 * An immutable authorization state. The amount is the original requested hold,
 * including in a SETTLED record. Only the latest APPROVED state reserves funds.
 */
public record Authorization(String accountId, String id, Money amount, Status status) {
    public enum Status { APPROVED, DECLINED, SETTLED }

    public Authorization {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(status, "status");
        if (accountId.isBlank() || id.isBlank()) {
            throw new CommandRejectedException("Account and authorization IDs must not be blank");
        }
        if (amount.signum() <= 0) {
            throw new CommandRejectedException("Authorization amount must be positive");
        }
    }
}
