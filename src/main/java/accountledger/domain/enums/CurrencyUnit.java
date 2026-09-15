package accountledger.domain.enums;

/**
 * Represents a currency unit with a specific scale.
 */
public enum CurrencyUnit {
    
    /**
     * United Arab Emirates Dirham (AED) with a scale of 2 decimal places.
     */
    AED(2), BHD(3);
   
    /**
     * Constructs a CurrencyUnit with the specified scale.
     *
     * @param scale the number of decimal places for the currency
     */
    private CurrencyUnit(int scale) {
        this.scale = scale;
    }

    private final int scale;

    /**
     * Returns the scale (number of decimal places) for the currency unit.
     */
    public int scale() {
        return scale;
    }
}
