package dk.via.partay.app.trace;

import java.time.Instant;
import java.util.List;

/** A finished REST request with every hop it caused. Read by the demo page. */
public record RequestTrace(long id, Instant startedAt, String method, String path, int status, long totalMs,
                           String actingAs, List<TraceHop> hops) {
}
