package dk.via.partay.app.web.dto;

public record TrackResponse(String spotifyTrackId, String title, String artist, String album, int durationMs,
                            String imageUrl) {
}
