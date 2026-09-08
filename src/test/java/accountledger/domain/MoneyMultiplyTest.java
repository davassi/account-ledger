package accountledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static accountledger.domain.CurrencyUnit.AED;
import static accountledger.domain.CurrencyUnit.BHD;
import static java.math.RoundingMode.HALF_EVEN;
import static java.math.RoundingMode.UNNECESSARY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyMultiplyTest {
    @ParameterizedTest
    @CsvSource({
            "AED, 250.00, 0.10",
            "AED, 235.00, 0.09",
            "AED, 12.50, 0.00",
            "AED, 37.50, 0.02",
            "BHD, 10.000, 0.004",
            "BHD, 1.250, 0.000",
            "BHD, 3.750, 0.002"
    })
    void dailyRateRoundsAtTheCurrencyScale(CurrencyUnit currency, String balance, String expected) {
        var result = Money.of(currency, balance).multiply(new BigDecimal("0.0004"), HALF_EVEN);

        assertEquals(Money.of(currency, expected), result);
    }

    @ParameterizedTest
    @CsvSource({"HALF_EVEN, 0.00", "HALF_UP, 0.01", "DOWN, 0.00", "UP, 0.01"})
    void theCallerChoosesHowToRoundATie(RoundingMode roundingMode, String expected) {
        var halfCent = Money.of(AED, "0.01").multiply(new BigDecimal("0.5"), roundingMode);

        assertEquals(Money.of(AED, expected), halfCent);
    }

    @Test
    void exactModeRejectsLostPrecision() {
        assertThrows(ArithmeticException.class,
                () -> Money.of(AED, "0.01").multiply(new BigDecimal("0.5"), UNNECESSARY));
        assertThrows(ArithmeticException.class,
                () -> Money.of(BHD, "0.001").multiply(new BigDecimal("0.5"), UNNECESSARY));
        assertEquals(Money.of(BHD, "0.002"), Money.of(BHD, "0.001").multiply(new BigDecimal("2"), UNNECESSARY));
    }

    @Test
    void factorPrecisionIsPreservedUntilTheFinalRounding() {
        var result = Money.of(AED, "100.00").multiply(new BigDecimal("0.000049"), HALF_EVEN);

        // Rounding either factor to 0.00005 first loses which side of the tie it lies on.
        assertEquals(Money.of(AED, "0.00"), result);
        assertEquals(Money.of(AED, "0.01"),
                Money.of(AED, "100.00").multiply(new BigDecimal("0.000051"), HALF_EVEN));
    }

    @ParameterizedTest
    @CsvSource({"-200.00, 0.5, -100.00, -1", "200.00, -0.5, -100.00, -1", "-200.00, -0.5, 100.00, 1", "200.00, 0, 0.00, 0"})
    void productsPreserveTheirSignIncludingZero(String amount, String factor, String expected, int sign) {
        var result = Money.of(AED, amount).multiply(new BigDecimal(factor), UNNECESSARY);

        assertEquals(Money.of(AED, expected), result);
        assertEquals(sign, result.signum());
    }
}
