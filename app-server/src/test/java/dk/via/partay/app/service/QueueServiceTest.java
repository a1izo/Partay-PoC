package dk.via.partay.app.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dk.via.partay.app.error.BadRequestException;
import dk.via.partay.app.error.ConflictException;
import dk.via.partay.app.error.ForbiddenException;
import dk.via.partay.app.error.NotFoundException;
import dk.via.partay.app.grpc.DataClient;
import dk.via.partay.app.model.GuestView;
import dk.via.partay.app.model.PartyView;
import dk.via.partay.app.model.QueueItemView;
import dk.via.partay.app.model.VoteView;
import dk.via.partay.app.spotify.SpotifyClient;
import dk.via.partay.app.spotify.TrackInfo;
import dk.via.partay.app.trace.TraceRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class QueueServiceTest {

    private static final String CODE = "K7QX2M";
    private static final String SOFIA_ID = "9a7e0000-0000-0000-0000-000000000001";
    private static final String TRACK_ID = "0VjIjW4GlUZAMYd2vXMi3b";
    private static final PartyView PARTY = new PartyView(1, CODE, "Friday pre-drinks", Instant.now());
    private static final GuestView SOFIA = new GuestView(SOFIA_ID, 1, "Sofia", "GUEST", Instant.now());
    private static final TrackInfo BLINDING = new TrackInfo(TRACK_ID, "Blinding Lights", "The Weeknd", "After Hours", 200040, null);
    private static final QueueItemView ITEM = new QueueItemView(3, 1, TRACK_ID, "Blinding Lights", "The Weeknd",
            "After Hours", 200040, SOFIA_ID, "Sofia", Instant.now());

    private DataClient data;
    private SpotifyClient spotify;
    private Cache<String, TrackInfo> trackCache;
    private QueueService service;

    @BeforeEach
    void setUp() {
        data = mock(DataClient.class);
        spotify = mock(SpotifyClient.class);
        trackCache = Caffeine.newBuilder().build();
        PartyService parties = new PartyService(data, new JoinCodeGenerator());
        service = new QueueService(data, spotify, parties, trackCache, new TraceRecorder());

        when(data.getPartyByJoinCode(CODE)).thenReturn(PARTY);
        when(data.getGuest(SOFIA_ID)).thenReturn(SOFIA);
        when(data.listQueueItems(1)).thenReturn(List.of(ITEM));
        when(data.listVotes(1)).thenReturn(List.of());
    }

    @Test
    void voteZeroCallsDeleteVote() {
        service.vote(CODE, SOFIA_ID, 3, 0);
        verify(data).deleteVote(3, SOFIA_ID);
        verify(data, never()).upsertVote(anyLong(), anyString(), anyInt());
    }

    @Test
    void voteOneCallsUpsertVote() {
        when(data.listVotes(1)).thenReturn(List.of(new VoteView(3, SOFIA_ID, 1, Instant.now())));
        QueueService.Queue queue = service.vote(CODE, SOFIA_ID, 3, 1);
        verify(data).upsertVote(3, SOFIA_ID, 1);
        verify(data, never()).deleteVote(anyLong(), anyString());
        assertEquals(1, queue.items().get(0).score());
        assertEquals(1, queue.items().get(0).myVote());
    }

    @Test
    void voteFiveIsBadRequest() {
        assertThrows(BadRequestException.class, () -> service.vote(CODE, SOFIA_ID, 3, 5));
        verifyNoInteractions(data);
    }

    @Test
    void voteOnItemOfAnotherPartyIsNotFound() {
        assertThrows(NotFoundException.class, () -> service.vote(CODE, SOFIA_ID, 99, 1));
        verify(data, never()).upsertVote(anyLong(), anyString(), anyInt());
    }

    @Test
    void guestFromAnotherPartyIsForbidden() {
        String larsId = "1a250000-0000-0000-0000-000000000002";
        when(data.getGuest(larsId)).thenReturn(new GuestView(larsId, 2, "Lars", "HOST", Instant.now()));
        assertThrows(ForbiddenException.class, () -> service.vote(CODE, larsId, 3, 1));
        verify(data, never()).upsertVote(anyLong(), anyString(), anyInt());
    }

    @Test
    void unknownOrInvalidGuestIdIsForbidden() {
        when(data.getGuest("00000000-0000-0000-0000-000000000000")).thenThrow(new NotFoundException("guest not found"));
        assertThrows(ForbiddenException.class, () -> service.vote(CODE, "00000000-0000-0000-0000-000000000000", 3, 1));
        assertThrows(ForbiddenException.class, () -> service.vote(CODE, "not-a-uuid", 3, 1));
    }

    @Test
    void addSongUsesTheCacheBeforeSpotify() {
        trackCache.put(TRACK_ID, BLINDING);
        when(data.addQueueItem(1, TRACK_ID, "Blinding Lights", "The Weeknd", "After Hours", 200040, SOFIA_ID))
                .thenReturn(ITEM);

        service.addSong(CODE, SOFIA_ID, TRACK_ID);

        verifyNoInteractions(spotify);
        verify(data).addQueueItem(1, TRACK_ID, "Blinding Lights", "The Weeknd", "After Hours", 200040, SOFIA_ID);
    }

    @Test
    void addSongFetchesFromSpotifyOnCacheMissAndCachesIt() {
        when(spotify.getTrack(TRACK_ID)).thenReturn(BLINDING);
        when(data.addQueueItem(anyLong(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(ITEM);

        service.addSong(CODE, SOFIA_ID, TRACK_ID);

        verify(spotify).getTrack(TRACK_ID);
        assertEquals(BLINDING, trackCache.getIfPresent(TRACK_ID));
    }

    @Test
    void addSongRejectsInvalidTrackId() {
        assertThrows(BadRequestException.class, () -> service.addSong(CODE, SOFIA_ID, "not a track"));
        verifyNoInteractions(spotify);
    }

    @Test
    void duplicateSongIsConflict() {
        trackCache.put(TRACK_ID, BLINDING);
        when(data.addQueueItem(anyLong(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenThrow(new ConflictException("Duplicate entry"));
        ConflictException e = assertThrows(ConflictException.class, () -> service.addSong(CODE, SOFIA_ID, TRACK_ID));
        assertEquals("This song is already in the queue", e.getMessage());
    }
}
