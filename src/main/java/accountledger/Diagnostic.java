package accountledger;

/** An anomaly on an accepted event; its account and reference are in the recorded request. */
public enum Diagnostic {
    MISSING_AUTHORIZATION,
    DECLINED_AUTHORIZATION
}
