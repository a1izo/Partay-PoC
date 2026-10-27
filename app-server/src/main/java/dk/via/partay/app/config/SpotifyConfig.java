package dk.via.partay.app.config;

import dk.via.partay.app.spotify.LiveSpotifyClient;
import dk.via.partay.app.spotify.MockSpotifyClient;
import dk.via.partay.app.spotify.SpotifyAuthClient;
import dk.via.partay.app.spotify.SpotifyClient;
import dk.via.partay.app.spotify.SpotifyProperties;
import dk.via.partay.app.trace.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

/** Picks the live Spotify client, or the mock when spotify.mode=mock or credentials are missing. */
@Configuration
public class SpotifyConfig {

    private static final Logger log = LoggerFactory.getLogger(SpotifyConfig.class);

    @Bean
    public SpotifyClient spotifyClient(SpotifyProperties props, RestClient.Builder builder, TraceRecorder traces) {
        if ("mock".equalsIgnoreCase(props.mode())) {
            log.info("[T2] Spotify mode: mock (built-in track list)");
            return new MockSpotifyClient(traces);
        }
        if (!props.hasCredentials()) {
            log.warn("[T2] Spotify credentials missing, using mock tracks");
            return new MockSpotifyClient(traces);
        }
        DefaultUriBuilderFactory uris = new DefaultUriBuilderFactory(props.apiUrl());
        uris.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES);
        RestClient restClient = builder.uriBuilderFactory(uris).build();
        log.info("[T2] Spotify mode: live ({})", props.apiUrl());
        return new LiveSpotifyClient(restClient, new SpotifyAuthClient(restClient, props, traces), traces);
    }
}
