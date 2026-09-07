package accountledger;

import java.util.Objects;

public record Account(String id, Money openingBalance) {

    /**
     * Constructs an Account instance with the specified ID and opening balance.
     *
     * @param id             the account ID
     * @param openingBalance the opening balance of the account
     * @throws NullPointerException     if id or openingBalance is null
     * @throws IllegalArgumentException if id is blank
     */
    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(openingBalance, "openingBalance");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Account ID must not be blank");
        }
    }
}
