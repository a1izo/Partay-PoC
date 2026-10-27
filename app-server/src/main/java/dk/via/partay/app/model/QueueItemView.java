package dk.via.partay.app.model;

import java.time.Instant;

public record QueueItemView(long id, long partyId, String spotifyTrackId, String title, String artist, String album,
                            int durationMs, String addedByGuestId, String addedByNickname, Instant addedAt) {
}
