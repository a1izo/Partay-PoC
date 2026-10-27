package dk.via.partay.app.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dk.via.partay.app.spotify.TrackInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/** Caffeine caches in front of Spotify. Used directly (not via @Cacheable) so responses can say cached: true/false. */
@Configuration
public class CacheConfig {

    /** key = lowercased normalized query + "|" + limit */
    @Bean
    public Cache<String, List<TrackInfo>> searchCache() {
        return Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(60)).maximumSize(500).build();
    }

    /** key = Spotify track ID. Every search result goes in here, so adding a song from a search skips Spotify. */
    @Bean
    public Cache<String, TrackInfo> trackCache() {
        return Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(30)).maximumSize(5000).build();
    }
}
