package dk.via.partay.app.spotify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import dk.via.partay.app.error.NotFoundException;
import dk.via.partay.app.error.UpstreamException;
import dk.via.partay.app.trace.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Calls the Spotify Web API with an app token (client credentials). Follows the February 2026
 * Development Mode rules: search limit at most 10, single GET /tracks/{id} only, and no
 * popularity / available_markets / linked_from fields.
 */
public class LiveSpotifyClient implements SpotifyClient {

    private static final Logger log = LoggerFactory.getLogger(LiveSpotifyClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;
    private final SpotifyAuthClient auth;
    private final TraceRecorder traces;

    /** @param restClient a client whose base URL is the Spotify API URL */
    public LiveSpotifyClient(RestClient restClient, SpotifyAuthClient auth, TraceRecorder traces) {
        this.restClient = restClient;
        this.auth = auth;
        this.traces = traces;
    }

    @Override
    public List<TrackInfo> search(String query, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 10));
        // Template variables are encoded strictly, so "&", "+" and non-ASCII characters in the query are safe.
        JsonNode body = get("/search", b -> b.path("/search")
                .queryParam("q", "{q}")
                .queryParam("type", "track")
                .queryParam("limit", "{limit}")
                .build(query, safeLimit));
        List<TrackInfo> tracks = new ArrayList<>();
        for (JsonNode item : body.path("tracks").path("items")) {
            tracks.add(toTrack(item));
        }
        return tracks;
    }

    @Override
    public TrackInfo getTrack(String spotifyTrackId) {
        return toTrack(get("/tracks/" + spotifyTrackId, b -> b.path("/tracks/{id}").build(spotifyTrackId)));
    }

    @Override
    public String mode() {
        return "live";
    }

    private record Response(int status, String retryAfter, JsonNode body) {
    }

    private JsonNode get(String hopName, Function<UriBuilder, URI> uri) {
        for (int attempt = 1; ; attempt++) {
            String token = auth.token();
            long start = System.nanoTime();
            Response r;
            try {
                r = restClient.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .exchange((req, res) -> new Response(res.getStatusCode().value(),
                                res.getHeaders().getFirst(HttpHeaders.RETRY_AFTER), readJson(res)));
            } catch (ResourceAccessException e) {
                traces.hop("SPOTIFY", "GET " + hopName, "ERROR", (System.nanoTime() - start) / 1_000_000);
                throw new UpstreamException(503, "Spotify unreachable: " + e.getMessage());
            }
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("[T2] HTTP -> Spotify GET {} {} ({} ms)", hopName, r.status(), ms);
            traces.hop("SPOTIFY", "GET " + hopName, String.valueOf(r.status()), ms);

            if (r.status() == 401 && attempt == 1) {
                auth.invalidate();
                continue;
            }
            if (r.status() >= 200 && r.status() < 300) {
                return r.body();
            }
            if (r.status() == 429) {
                String wait = r.retryAfter() == null ? "?" : r.retryAfter();
                throw new UpstreamException(503, "Spotify rate limit, retry in " + wait + " s");
            }
            if (r.status() == 404) {
                throw new NotFoundException("Track not found on Spotify");
            }
            String message = r.body().path("error").path("message").asText("");
            throw new UpstreamException(502, "Spotify returned " + r.status() + (message.isEmpty() ? "" : ": " + message));
        }
    }

    static JsonNode readJson(ClientHttpResponse res) throws IOException {
        try (InputStream in = res.getBody()) {
            byte[] bytes = in.readAllBytes();
            return bytes.length == 0 ? MissingNode.getInstance() : JSON.readTree(bytes);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return MissingNode.getInstance();
        }
    }

    private static TrackInfo toTrack(JsonNode t) {
        List<String> artists = new ArrayList<>();
        for (JsonNode a : t.path("artists")) {
            artists.add(a.path("name").asText());
        }
        // Smallest album image: Spotify lists them widest first, but do not rely on the order.
        String image = null;
        int smallest = Integer.MAX_VALUE;
        for (JsonNode img : t.path("album").path("images")) {
            int width = img.path("width").asInt(Integer.MAX_VALUE - 1);
            if (width < smallest) {
                smallest = width;
                image = img.path("url").asText(null);
            }
        }
        return new TrackInfo(t.path("id").asText(), t.path("name").asText(), String.join(", ", artists),
                t.path("album").path("name").asText(""), t.path("duration_ms").asInt(), image);
    }
}
