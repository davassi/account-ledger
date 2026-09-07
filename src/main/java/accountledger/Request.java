package accountledger;

import java.util.List;
import java.util.Objects;

/** Original event input. Record equality defines whether a submission is a retry. */
public record Request(String eventId, int bookedDay, Data data) {

    /** Constructs a Request instance with the specified event ID, booked day, and data. */
    public Request {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(data, "data");
        if (eventId.isBlank()) {
            throw new IllegalArgumentException("Event ID must not be blank");
        }
        if (bookedDay < 1) {
            throw new IllegalArgumentException("Booked day must be positive");
        }
    }

    /** The record type distinguishes the operation as well as its input fields. */
    public sealed interface Data permits Postings, Hold, Settlement, Reversal, DayClose, FeeReassessment {}

    public record Postings(List<Posting> postings) implements Data {
        public Postings {
            postings = List.copyOf(postings);
        }
    }

    /** Hold input; the authorization operation validates it before recording an outcome. */
    public record Hold(String accountId, String authorizationId, Money amount) implements Data {}

    /** The monetary value day is independent of the request's booked day. */
    public record Settlement(String accountId, String authorizationId, Money amount, int valueDay) implements Data {}

    /** Amounts and currency come from the original event, not from the reversal request. */
    public record Reversal(String accountId, String originalEventId, int valueDay) implements Data {}

    /** Closes the request's booked day, including any newly assessed fees. */
    public record DayClose(String accountId) implements Data {}

    /** Rechecks already closed days using the knowledge available when recorded. */
    public record FeeReassessment(String accountId) implements Data {}
}
