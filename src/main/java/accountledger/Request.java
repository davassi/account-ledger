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

    /** The record type also distinguishes monetary input from a hold request. */
    public sealed interface Data permits Postings, Hold {}

    public record Postings(List<Posting> postings) implements Data {
        public Postings {
            postings = List.copyOf(postings);
        }
    }

    /** Hold input; the authorization operation validates it before recording an outcome. */
    public record Hold(String accountId, String authorizationId, Money amount) implements Data {}
}
