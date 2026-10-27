package dk.via.partay.app.trace;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory request trace. A ThreadLocal holds the trace of the current request; this works because
 * the blocking gRPC stub and the Spotify client both run on the request thread. Finished traces go
 * into a ring buffer of the last {@value #CAPACITY}.
 */
@Component
public class TraceRecorder {

    public static final int CAPACITY = 100;

    private final ThreadLocal<Active> current = new ThreadLocal<>();
    private final ArrayDeque<RequestTrace> buffer = new ArrayDeque<>();
    private final AtomicLong nextId = new AtomicLong(1);
    // guestId -> nickname, learned from T3 responses, so a trace can say who acted without an extra call
    private final Map<String, String> knownGuests = new ConcurrentHashMap<>();

    private static final class Active {
        final Instant startedAt = Instant.now();
        final long startNanos = System.nanoTime();
        final String method;
        final String path;
        final String guestId;
        final List<TraceHop> hops = new ArrayList<>();

        Active(String method, String path, String guestId) {
            this.method = method;
            this.path = path;
            this.guestId = guestId;
        }
    }

    public void start(String method, String path, String guestId) {
        current.set(new Active(method, path, guestId));
    }

    /** Adds a hop to the current request. Does nothing outside a traced request. */
    public void hop(String type, String name, String status, long ms) {
        Active active = current.get();
        if (active != null) {
            active.hops.add(new TraceHop(type, name, status, ms));
        }
    }

    /** Finishes the current trace and stores it. Always clears the ThreadLocal. */
    public RequestTrace finish(int status) {
        Active active = current.get();
        current.remove();
        if (active == null) {
            return null;
        }
        long totalMs = (System.nanoTime() - active.startNanos) / 1_000_000;
        String actingAs = active.guestId == null ? null : knownGuests.get(active.guestId.trim().toLowerCase());
        RequestTrace trace = new RequestTrace(nextId.getAndIncrement(), active.startedAt, active.method, active.path,
                status, totalMs, actingAs, List.copyOf(active.hops));
        synchronized (buffer) {
            buffer.addLast(trace);
            while (buffer.size() > CAPACITY) {
                buffer.removeFirst();
            }
        }
        return trace;
    }

    public void clearCurrent() {
        current.remove();
    }

    public void rememberGuest(String guestId, String nickname) {
        knownGuests.put(guestId.toLowerCase(), nickname);
    }

    /** Traces with an id greater than {@code afterId}, oldest first. */
    public List<RequestTrace> after(long afterId) {
        synchronized (buffer) {
            return buffer.stream().filter(t -> t.id() > afterId).toList();
        }
    }

    public void reset() {
        synchronized (buffer) {
            buffer.clear();
        }
    }
}
