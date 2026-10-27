package dk.via.partay.app.web.dto;

import java.time.Instant;

public record QueueEntryResponse(int position, long itemId, String spotifyTrackId, String title, String artist,
                                 String album, String addedBy, int score, int upvotes, int downvotes, int myVote,
                                 int durationMs, Instant addedAt) {
}
