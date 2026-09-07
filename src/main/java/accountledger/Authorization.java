package accountledger;

import java.util.Objects;

/**
 * An immutable approval or decline. The requested amount becomes an active hold
 * only when approved; neither outcome is a monetary posting.
 */
public record Authorization(String accountId, String id, Money amount, Status status) {
    public enum Status { APPROVED, DECLINED }

    public Authorization {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(status, "status");
        if (accountId.isBlank() || id.isBlank()) {
            throw new IllegalArgumentException("Account and authorization IDs must not be blank");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Authorization amount must be positive");
        }
    }
}
