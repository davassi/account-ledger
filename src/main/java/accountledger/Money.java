package accountledger;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

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

    /** Returns -1, 0 or 1 for a negative, zero or positive amount. */
    public int signum() {
        return amount.signum();
    }

    /**
     * Multiplies exactly, then rounds once to this currency's scale. The caller
     * chooses the rounding policy; UNNECESSARY rejects any lost precision.
     */
    public Money multiply(BigDecimal factor, RoundingMode roundingMode) {
        return new Money(currency, amount.multiply(factor).setScale(currency.scale(), roundingMode));
    }

    public List<Money> split(int parts) {
        if (parts <= 0) {
            throw new IllegalArgumentException("Parts must be positive");
        }
        var division = amount.unscaledValue().divideAndRemainder(BigInteger.valueOf(parts));
        int remainder = division[1].abs().intValueExact();
        var adjustment = BigInteger.valueOf(amount.signum());
        return IntStream.range(0, parts)
                .mapToObj(i -> division[0].add(i < remainder ? adjustment : BigInteger.ZERO))
                .map(units -> new Money(currency, new BigDecimal(units, currency.scale())))
                .toList();
    }
}
