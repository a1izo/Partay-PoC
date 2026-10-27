package dk.via.partay.app.trace;

/**
 * One hop T2 made while serving a request.
 *
 * @param type   GRPC, SPOTIFY or CACHE
 * @param name   for example {@code UpsertVote}, {@code GET /search}, {@code search "blinding"}
 * @param status for example {@code OK}, {@code NOT_FOUND}, {@code 200}, {@code HIT}
 */
public record TraceHop(String type, String name, String status, long ms) {
}
