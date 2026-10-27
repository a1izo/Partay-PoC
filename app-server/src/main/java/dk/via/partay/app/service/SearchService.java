package dk.via.partay.app.service;

import com.github.benmanes.caffeine.cache.Cache;
import dk.via.partay.app.error.BadRequestException;
import dk.via.partay.app.spotify.SpotifyClient;
import dk.via.partay.app.spotify.TrackInfo;
import dk.via.partay.app.trace.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/** Search rules and the search cache in front of Spotify. */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    public static final int DEFAULT_LIMIT = 5;

    private final SpotifyClient spotify;
    private final Cache<String, List<TrackInfo>> searchCache;
    private final Cache<String, TrackInfo> trackCache;
    private final TraceRecorder traces;

    public SearchService(SpotifyClient spotify, Cache<String, List<TrackInfo>> searchCache,
                         Cache<String, TrackInfo> trackCache, TraceRecorder traces) {
        this.spotify = spotify;
        this.searchCache = searchCache;
        this.trackCache = trackCache;
        this.traces = traces;
    }

    public record Result(String query, boolean cached, long tookMs, List<TrackInfo> tracks) {
    }

    public Result search(String rawQuery, Integer rawLimit) {
        long start = System.nanoTime();
        String query = rawQuery == null ? "" : rawQuery.trim().replaceAll("\\s+", " ");
        if (query.length() < 2 || query.length() > 100) {
            throw new BadRequestException("Search query must be 2 to 100 characters");
        }
        int limit = rawLimit == null ? DEFAULT_LIMIT : rawLimit;
        if (limit < 1 || limit > 10) {
            throw new BadRequestException("limit must be 1 to 10");
        }

        String key = query.toLowerCase(Locale.ROOT) + "|" + limit;
        List<TrackInfo> tracks = searchCache.getIfPresent(key);
        boolean cached = tracks != null;
        if (cached) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("[T2] cache hit search \"{}\"", query);
            traces.hop("CACHE", "search \"" + query + "\"", "HIT", ms);
        } else {
            tracks = spotify.search(query, limit);
            searchCache.put(key, tracks);
            tracks.forEach(t -> trackCache.put(t.spotifyTrackId(), t));
        }
        return new Result(query, cached, (System.nanoTime() - start) / 1_000_000, tracks);
    }
}
