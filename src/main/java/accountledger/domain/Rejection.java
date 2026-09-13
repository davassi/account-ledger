package accountledger.domain;

/** One failed attempt, dated when processed, retaining its original command. */
public record Rejection(int processingDay, Command command, String reason) {}
