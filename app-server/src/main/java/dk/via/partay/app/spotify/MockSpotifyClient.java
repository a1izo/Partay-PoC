package dk.via.partay.app.spotify;

import dk.via.partay.app.error.NotFoundException;
import dk.via.partay.app.trace.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Built-in list of 20 well-known tracks. Keeps the demo alive when Spotify or the network fails.
 * IDs are 22 characters ("mock" + 18 digits), so they pass the same validation as real Spotify IDs.
 */
public class MockSpotifyClient implements SpotifyClient {

    private static final Logger log = LoggerFactory.getLogger(MockSpotifyClient.class);

    private static final List<TrackInfo> TRACKS = List.of(
            track(1, "Blinding Lights", "The Weeknd", "After Hours", 200040),
            track(2, "Mr. Brightside", "The Killers", "Hot Fuss", 222973),
            track(3, "September", "Earth, Wind & Fire", "The Best of Earth, Wind & Fire, Vol. 1", 215000),
            track(4, "Dancing Queen", "ABBA", "Arrival", 230400),
            track(5, "Dancing On My Own", "Robyn", "Body Talk", 281000),
            track(6, "Uptown Funk", "Mark Ronson, Bruno Mars", "Uptown Special", 269666),
            track(7, "Shape of You", "Ed Sheeran", "Divide", 233712),
            track(8, "Levitating", "Dua Lipa", "Future Nostalgia", 203064),
            track(9, "Bohemian Rhapsody", "Queen", "A Night at the Opera", 354320),
            track(10, "Don't Stop Me Now", "Queen", "Jazz", 209413),
            track(11, "Billie Jean", "Michael Jackson", "Thriller", 293826),
            track(12, "Wake Me Up", "Avicii", "True", 247426),
            track(13, "Get Lucky", "Daft Punk, Pharrell Williams", "Random Access Memories", 369626),
            track(14, "Rolling in the Deep", "Adele", "21", 228093),
            track(15, "Sweet Child O' Mine", "Guns N' Roses", "Appetite for Destruction", 356066),
            track(16, "As It Was", "Harry Styles", "Harry's House", 167303),
            track(17, "Bad Guy", "Billie Eilish", "When We All Fall Asleep, Where Do We Go?", 194088),
            track(18, "Happy", "Pharrell Williams", "G I R L", 232720),
            track(19, "Hey Ya!", "OutKast", "Speakerboxxx/The Love Below", 235213),
            track(20, "Take On Me", "a-ha", "Hunting High and Low", 225280));

    private final TraceRecorder traces;

    public MockSpotifyClient(TraceRecorder traces) {
        this.traces = traces;
    }

    @Override
    public List<TrackInfo> search(String query, int limit) {
        long start = System.nanoTime();
        // Every word of the query must appear in "title artist", ignoring case and punctuation.
        String[] words = normalize(query).split(" ");
        List<TrackInfo> result = TRACKS.stream()
                .filter(t -> {
                    String haystack = normalize(t.title() + " " + t.artist());
                    return Arrays.stream(words).allMatch(haystack::contains);
                })
                .limit(Math.max(1, Math.min(limit, 10)))
                .toList();
        long ms = (System.nanoTime() - start) / 1_000_000;
        log.info("[T2] mock Spotify search \"{}\" {} results ({} ms)", query, result.size(), ms);
        traces.hop("SPOTIFY", "mock search", "OK", ms);
        return result;
    }

    @Override
    public TrackInfo getTrack(String spotifyTrackId) {
        traces.hop("SPOTIFY", "mock track", "OK", 0);
        return TRACKS.stream()
                .filter(t -> t.spotifyTrackId().equals(spotifyTrackId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Track not found on Spotify"));
    }

    @Override
    public String mode() {
        return "mock";
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} ]", "").replaceAll("\\s+", " ").trim();
    }

    private static TrackInfo track(int n, String title, String artist, String album, int durationMs) {
        return new TrackInfo(String.format("mock%018d", n), title, artist, album, durationMs, null);
    }
}
