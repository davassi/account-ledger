package accountledger;

import java.util.Objects;

public record Posting(String accountId, int valueDay, Money delta) {
    public Posting {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(delta, "delta");
        if (accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID must not be blank");
        }
        if (valueDay < 1) {
            throw new IllegalArgumentException("Value day must be positive");
        }
    }
}
