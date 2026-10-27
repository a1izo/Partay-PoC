package dk.via.partay.app.model;

import java.time.Instant;

public record VoteView(long queueItemId, String guestId, int value, Instant updatedAt) {
}
