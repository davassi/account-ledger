package accountledger;

public enum CurrencyUnit {
    AED(2), BHD(3);

    private final int scale;

    CurrencyUnit(int scale) {
        this.scale = scale;
    }

    public int scale() {
        return scale;
    }
}
