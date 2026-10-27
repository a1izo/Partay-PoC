package dk.via.partay.app.web;

import dk.via.partay.app.trace.RequestTrace;
import dk.via.partay.app.trace.TraceRecorder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Request traces for the demo page. Requests to /api/debug are never traced themselves. */
@RestController
@RequestMapping("/api/debug/traces")
@Tag(name = "Debug", description = "Hops (gRPC, Spotify, cache) of the last 100 requests")
public class DebugController {

    private final TraceRecorder traces;

    public DebugController(TraceRecorder traces) {
        this.traces = traces;
    }

    @GetMapping
    @Operation(summary = "Traces with id greater than after, oldest first")
    public List<RequestTrace> list(@RequestParam(defaultValue = "0") long after) {
        return traces.after(after);
    }

    @DeleteMapping
    @Operation(summary = "Clear the trace buffer")
    public ResponseEntity<Void> clear() {
        traces.reset();
        return ResponseEntity.noContent().build();
    }
}
