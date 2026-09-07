package accountledger;

/** One failed attempt, dated when processed, retaining its original request. */
public record Rejection(int processingDay, Request request, String reason) {}
