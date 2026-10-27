package dk.via.partay.app.spotify;

import com.fasterxml.jackson.databind.JsonNode;
import dk.via.partay.app.error.UpstreamException;
import dk.via.partay.app.trace.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Client credentials flow. Caches the token in memory and fetches a new one 60 seconds before it expires.
 * Thread-safe: callers synchronize on this object.
 */
public class SpotifyAuthClient {

    private static final Logger log = LoggerFactory.getLogger(SpotifyAuthClient.class);
    private static final long REFRESH_MARGIN_SECONDS = 60;

    private final RestClient restClient;
    private final SpotifyProperties props;
    private final TraceRecorder traces;

    private String token;
    private Instant refreshAt = Instant.EPOCH;

    public SpotifyAuthClient(RestClient restClient, SpotifyProperties props, TraceRecorder traces) {
        this.restClient = restClient;
        this.props = props;
        this.traces = traces;
    }

    public synchronized String token() {
        if (token == null || Instant.now().isAfter(refreshAt)) {
            fetch();
        }
        return token;
    }

    public synchronized void invalidate() {
        token = null;
    }

    private void fetch() {
        String basic = Base64.getEncoder().encodeToString(
                (props.clientId() + ":" + props.clientSecret()).getBytes(StandardCharsets.UTF_8));
        long start = System.nanoTime();
        record Result(int status, JsonNode body) {
        }
        Result result;
        try {
            result = restClient.post()
                    .uri(props.accountsUrl() + "/api/token")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials")
                    .exchange((req, res) -> new Result(res.getStatusCode().value(), LiveSpotifyClient.readJson(res)));
        } catch (ResourceAccessException e) {
            traces.hop("SPOTIFY", "POST /api/token", "ERROR", (System.nanoTime() - start) / 1_000_000);
            throw new UpstreamException(503, "Spotify unreachable: " + e.getMessage());
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        log.info("[T2] HTTP -> Spotify POST /api/token {} ({} ms)", result.status(), ms);
        traces.hop("SPOTIFY", "POST /api/token", String.valueOf(result.status()), ms);
        if (result.status() != 200 || !result.body().hasNonNull("access_token")) {
            String error = result.body().path("error").asText("");
            throw new UpstreamException(502, "Spotify token request failed: " + result.status() + " " + error);
        }
        token = result.body().get("access_token").asText();
        long expiresIn = result.body().path("expires_in").asLong(3600);
        refreshAt = Instant.now().plusSeconds(Math.max(0, expiresIn - REFRESH_MARGIN_SECONDS));
    }
}
