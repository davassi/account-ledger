package accountledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(CurrencyUnit currency, BigDecimal amount) {
    public Money {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
        amount = amount.setScale(currency.scale(), RoundingMode.UNNECESSARY);
    }

    public static Money of(CurrencyUnit currency, String amount) {
        return new Money(currency, new BigDecimal(amount));
    }

    public Money add(Money other) {
        if (currency != other.currency) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " and " + other.currency);
        }
        return new Money(currency, amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return add(other.negate());
    }

    public Money negate() {
        return new Money(currency, amount.negate());
    }
}
