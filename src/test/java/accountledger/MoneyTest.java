package accountledger;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static accountledger.CurrencyUnit.AED;
import static accountledger.CurrencyUnit.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {
    @Test
    void normalizesAmountsToTheirCurrencyScale() {
        assertEquals(new BigDecimal("1.20"), Money.of(AED, "1.2000").amount());
        assertEquals(new BigDecimal("1.200"), Money.of(BHD, "1.2").amount());
        assertEquals(new BigDecimal("0.000"), Money.of(BHD, "0").amount());
    }

    @Test
    void equivalentRepresentationsHaveEqualValuesAndHashes() {
        var first = Money.of(AED, "1.2");
        var second = Money.of(AED, "1.2000");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void rejectsAmountsThatWouldRequireRounding() {
        assertThrows(ArithmeticException.class, () -> Money.of(AED, "1.001"));
        assertThrows(ArithmeticException.class, () -> Money.of(BHD, "1.0001"));
    }

    @Test
    void addsDecimalAmountsExactly() {
        assertEquals(Money.of(AED, "0.30"), Money.of(AED, "0.10").add(Money.of(AED, "0.20")));
        assertEquals(Money.of(BHD, "0.003"), Money.of(BHD, "0.001").add(Money.of(BHD, "0.002")));
    }

    @Test
    void subtractionCanProduceAnOverdraft() {
        var balance = Money.of(AED, "1200.00").subtract(Money.of(AED, "950.00"));

        assertEquals(Money.of(AED, "250.00"), balance);
        assertEquals(Money.of(AED, "-370.00"), balance.subtract(Money.of(AED, "620.00")));
    }

    @Test
    void anAmountAndItsReversalCancelExactly() {
        var debit = Money.of(AED, "-620.00");

        assertEquals(Money.of(AED, "620.00"), debit.negate());
        assertEquals(Money.of(AED, "0.00"), debit.add(debit.negate()));
    }

    @Test
    void rejectsMixedCurrencyArithmetic() {
        var aed = Money.of(AED, "1.00");
        var bhd = Money.of(BHD, "1.000");

        assertThrows(IllegalArgumentException.class, () -> aed.add(bhd));
        assertThrows(IllegalArgumentException.class, () -> aed.subtract(bhd));
        assertThrows(IllegalArgumentException.class, () -> bhd.add(aed));
        assertThrows(IllegalArgumentException.class, () -> bhd.subtract(aed));
    }

    @Test
    void requiresBothCurrencyAndAmount() {
        assertThrows(NullPointerException.class, () -> new Money(null, BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> new Money(AED, null));
    }
}
