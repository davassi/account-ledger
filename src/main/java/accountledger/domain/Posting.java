package accountledger.domain;

import java.util.Objects;

public record Posting(String accountId, int valueDay, Money delta) {

    /**
     * Constructs a Posting instance with the specified account ID, value day, and delta.
     */
    public Posting {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(delta, "delta");
        if (accountId.isBlank()) {
            throw new CommandRejectedException("Account ID must not be blank");
        }
        if (valueDay < 1) {
            throw new CommandRejectedException("Value day must be positive");
        }
    }
}
