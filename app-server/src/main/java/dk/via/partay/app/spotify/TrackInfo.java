package dk.via.partay.app.spotify;

/** Track data as Spotify returns it. {@code previewImageUrl} is the smallest album image, or null. */
public record TrackInfo(String spotifyTrackId, String title, String artist, String album, int durationMs,
                        String previewImageUrl) {
}
