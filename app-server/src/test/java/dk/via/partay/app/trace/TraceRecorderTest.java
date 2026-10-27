package dk.via.partay.app.trace;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceRecorderTest {

    private final TraceRecorder recorder = new TraceRecorder();

    @Test
    void hopsAttachToTheCurrentTrace() {
        recorder.start("PUT", "/api/parties/K7QX2M/queue/3/vote", null);
        recorder.hop("GRPC", "GetGuest", "OK", 3);
        recorder.hop("GRPC", "UpsertVote", "OK", 4);
        RequestTrace trace = recorder.finish(200);

        assertEquals(List.of(new TraceHop("GRPC", "GetGuest", "OK", 3), new TraceHop("GRPC", "UpsertVote", "OK", 4)),
                trace.hops());
        assertEquals(200, trace.status());
        assertEquals("PUT", trace.method());
    }

    @Test
    void hopsFromAnotherThreadGoToThatThreadsTrace() throws InterruptedException {
        recorder.start("GET", "/a", null);
        recorder.hop("GRPC", "A", "OK", 1);

        AtomicReference<RequestTrace> other = new AtomicReference<>();
        Thread t = new Thread(() -> {
            recorder.start("GET", "/b", null);
            recorder.hop("GRPC", "B", "OK", 1);
            other.set(recorder.finish(200));
        });
        t.start();
        t.join();

        RequestTrace mine = recorder.finish(200);
        assertEquals(List.of("A"), mine.hops().stream().map(TraceHop::name).toList());
        assertEquals(List.of("B"), other.get().hops().stream().map(TraceHop::name).toList());
    }

    @Test
    void hopOutsideARequestIsIgnored() {
        recorder.hop("GRPC", "Ping", "OK", 1);
        assertNull(recorder.finish(200));
        assertTrue(recorder.after(0).isEmpty());
    }

    @Test
    void actingAsComesFromKnownGuests() {
        recorder.rememberGuest("9A7E0000-0000-0000-0000-000000000000", "Sofia");
        recorder.start("GET", "/x", "9a7e0000-0000-0000-0000-000000000000");
        assertEquals("Sofia", recorder.finish(200).actingAs());
    }

    @Test
    void bufferKeepsOnlyTheLast100() {
        for (int i = 0; i < 150; i++) {
            recorder.start("GET", "/r" + i, null);
            recorder.finish(200);
        }
        List<RequestTrace> all = recorder.after(0);
        assertEquals(TraceRecorder.CAPACITY, all.size());
        assertEquals("/r50", all.get(0).path());
        assertEquals("/r149", all.get(all.size() - 1).path());
    }

    @Test
    void afterFiltersByIdOldestFirst() {
        for (int i = 0; i < 5; i++) {
            recorder.start("GET", "/r" + i, null);
            recorder.finish(200);
        }
        List<RequestTrace> after = recorder.after(3);
        assertEquals(List.of(4L, 5L), after.stream().map(RequestTrace::id).toList());
    }

    @Test
    void resetClearsTheBuffer() {
        recorder.start("GET", "/r", null);
        recorder.finish(200);
        recorder.reset();
        assertTrue(recorder.after(0).isEmpty());
    }
}
