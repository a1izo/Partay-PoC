package dk.via.partay.app.spotify;

import java.util.List;

public interface SpotifyClient {

    /** @param limit 1 to 10 (Spotify's maximum since February 2026) */
    List<TrackInfo> search(String query, int limit);

    TrackInfo getTrack(String spotifyTrackId);

    /** "live" or "mock", shown by the health endpoint. */
    String mode();
}
