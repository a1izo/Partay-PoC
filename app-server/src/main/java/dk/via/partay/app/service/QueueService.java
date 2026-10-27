package dk.via.partay.app.service;

import com.github.benmanes.caffeine.cache.Cache;
import dk.via.partay.app.error.BadRequestException;
import dk.via.partay.app.error.ConflictException;
import dk.via.partay.app.error.NotFoundException;
import dk.via.partay.app.grpc.DataClient;
import dk.via.partay.app.model.PartyView;
import dk.via.partay.app.model.QueueItemView;
import dk.via.partay.app.model.RankedItem;
import dk.via.partay.app.spotify.SpotifyClient;
import dk.via.partay.app.spotify.TrackInfo;
import dk.via.partay.app.trace.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/** Queue and vote rules. */
@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);
    private static final Pattern TRACK_ID = Pattern.compile("^[0-9A-Za-z]{22}$");

    private final DataClient data;
    private final SpotifyClient spotify;
    private final PartyService parties;
    private final Cache<String, TrackInfo> trackCache;
    private final TraceRecorder traces;

    public QueueService(DataClient data, SpotifyClient spotify, PartyService parties,
                        Cache<String, TrackInfo> trackCache, TraceRecorder traces) {
        this.data = data;
        this.spotify = spotify;
        this.parties = parties;
        this.trackCache = trackCache;
        this.traces = traces;
    }

    public record Queue(PartyView party, List<RankedItem> items) {
    }

    /** The client sends only the track ID. Title, artist and duration always come from Spotify. */
    public QueueItemView addSong(String joinCode, String guestId, String rawTrackId) {
        String trackId = rawTrackId == null ? "" : rawTrackId.trim();
        if (!TRACK_ID.matcher(trackId).matches()) {
            throw new BadRequestException("spotifyTrackId must be 22 letters or digits");
        }
        PartyService.Member member = parties.requireMember(joinCode, guestId);

        TrackInfo track = trackCache.getIfPresent(trackId);
        if (track != null) {
            log.info("[T2] cache hit track {}", trackId);
            traces.hop("CACHE", "track " + trackId, "HIT", 0);
        } else {
            track = spotify.getTrack(trackId);
            trackCache.put(trackId, track);
        }

        try {
            return data.addQueueItem(member.party().id(), track.spotifyTrackId(), track.title(), track.artist(),
                    track.album(), track.durationMs(), member.guest().id());
        } catch (ConflictException e) {
            throw new ConflictException("This song is already in the queue");
        }
    }

    /** value 1 or -1 replaces any earlier vote by this guest; 0 clears it. Returns the ranked queue. */
    public Queue vote(String joinCode, String guestId, long itemId, Integer value) {
        if (value == null || value < -1 || value > 1) {
            throw new BadRequestException("Vote value must be -1, 0 or 1");
        }
        PartyService.Member member = parties.requireMember(joinCode, guestId);
        List<QueueItemView> items = data.listQueueItems(member.party().id());
        if (items.stream().noneMatch(i -> i.id() == itemId)) {
            throw new NotFoundException("Queue item " + itemId + " not found in party " + member.party().joinCode());
        }
        if (value == 0) {
            data.deleteVote(itemId, member.guest().id()); // clearing a vote that does not exist is fine
        } else {
            data.upsertVote(itemId, member.guest().id(), value);
        }
        return new Queue(member.party(),
                QueueRanker.rank(items, data.listVotes(member.party().id()), member.guest().id()));
    }

    /** @param guestId optional, only used for myVote */
    public Queue getQueue(String joinCode, String guestId) {
        PartyView party = parties.getParty(joinCode);
        String me = guestId == null || guestId.isBlank() ? null : guestId.trim();
        return new Queue(party, QueueRanker.rank(data.listQueueItems(party.id()), data.listVotes(party.id()), me));
    }
}
