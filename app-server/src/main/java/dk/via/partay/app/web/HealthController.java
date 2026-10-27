package dk.via.partay.app.web;

import dk.via.partay.app.error.UpstreamException;
import dk.via.partay.app.grpc.DataClient;
import dk.via.partay.app.spotify.SpotifyClient;
import dk.via.partay.app.web.dto.HealthResponse;
import dk.via.partay.grpc.PingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Health")
public class HealthController {

    private final DataClient data;
    private final SpotifyClient spotify;

    public HealthController(DataClient data, SpotifyClient spotify) {
        this.data = data;
        this.spotify = spotify;
    }

    @GetMapping("/api/health")
    @Operation(summary = "Health through all tiers", description = "T2 pings T3, T3 runs SELECT 1 on MySQL.")
    public ResponseEntity<HealthResponse> health() {
        long start = System.nanoTime();
        try {
            PingResponse ping = data.ping();
            long ms = (System.nanoTime() - start) / 1_000_000;
            String db = ping.getDatabaseOk() ? "UP" : "DOWN";
            return ResponseEntity.status(ping.getDatabaseOk() ? 200 : 503)
                    .body(new HealthResponse("UP", "UP", db, spotify.mode(), ms));
        } catch (UpstreamException e) {
            return ResponseEntity.status(503)
                    .body(new HealthResponse("UP", "DOWN", "UNKNOWN", spotify.mode(), null));
        }
    }
}
