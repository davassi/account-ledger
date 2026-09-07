package accountledger;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static accountledger.CurrencyUnit.AED;
import static accountledger.CurrencyUnit.BHD;


@SuppressWarnings("ThrowableResultIgnored") // NetBeans: assertThrows returns the Throwable by design
class MoneySplitTest {
    
    @ParameterizedTest
    @CsvSource({
            "BHD, 10.000, 3.334, 3.333, 3.333",
            "AED, 10.00, 3.34, 3.33, 3.33",
            "BHD, -10.000, -3.334, -3.333, -3.333",
            "AED, 0.02, 0.01, 0.01, 0.00",
            "AED, -0.02, -0.01, -0.01, 0.00",
            "BHD, 0.000, 0.000, 0.000, 0.000",
            "AED, 9.00, 3.00, 3.00, 3.00",
            "AED, 92233720368547758.08, 30744573456182586.03, 30744573456182586.03, 30744573456182586.02"
    })
    void distributesRemaindersWithoutChangingTheTotal(
            CurrencyUnit currency, String total, String first, String second, String third) {
        var amount = Money.of(currency, total);
        var parts = amount.split(3);

        assertEquals(List.of(Money.of(currency, first), Money.of(currency, second), Money.of(currency, third)), parts);
        assertEquals(amount, parts.stream().reduce(Money.of(currency, "0"), Money::add));
    }

    @Test
    void onePartPreservesTheWholeAmount() {
        var amount = Money.of(BHD, "10.000");
        assertEquals(List.of(amount), amount.split(1));
    }

    @Test
    void splitsIntoTwoEqualPartsWhenThereIsNoRemainder() {
        var half = Money.of(BHD, "5.000");
        assertEquals(List.of(half, half), Money.of(BHD, "10.000").split(2));
    }
    
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveCounts(int parts) {
        assertThrows(IllegalArgumentException.class, () -> Money.of(AED, "10.00").split(parts));
    }

    @Test
    void returnsAnImmutableList() {
        var parts = Money.of(BHD, "10.000").split(3);

        assertThrows(UnsupportedOperationException.class, () -> parts.set(0, Money.of(BHD, "0.000")));
    }
}
