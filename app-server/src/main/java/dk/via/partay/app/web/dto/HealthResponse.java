package dk.via.partay.app.web.dto;

/** @param database UP, DOWN or UNKNOWN (when the data-server cannot be reached) */
public record HealthResponse(String appServer, String dataServer, String database, String spotifyMode, Long roundTripMs) {
}
