package dk.via.partay.app.web.dto;

import java.time.Instant;

public record QueueItemAddedResponse(long itemId, String spotifyTrackId, String title, String artist, String album,
                                     int durationMs, String addedBy, Instant addedAt) {
}
