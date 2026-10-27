package dk.via.partay.app.grpc;

import dk.via.partay.app.error.BadRequestException;
import dk.via.partay.app.error.ConflictException;
import dk.via.partay.app.error.NotFoundException;
import dk.via.partay.app.error.UpstreamException;
import dk.via.partay.app.model.GuestView;
import dk.via.partay.app.model.PartyView;
import dk.via.partay.app.model.QueueItemView;
import dk.via.partay.app.model.VoteView;
import dk.via.partay.app.trace.TraceRecorder;
import dk.via.partay.grpc.*;
import io.grpc.StatusRuntimeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * T2's only way into T3. One method per RPC. Converts proto messages to T2 records and gRPC
 * status codes to T2 exceptions.
 */
@Component
public class DataClient {

    private final PartayDataServiceGrpc.PartayDataServiceBlockingStub stub;
    private final long deadlineMs;
    private final TraceRecorder traces;

    public DataClient(PartayDataServiceGrpc.PartayDataServiceBlockingStub stub,
                      @Value("${data-server.deadline-ms}") long deadlineMs, TraceRecorder traces) {
        this.stub = stub;
        this.deadlineMs = deadlineMs;
        this.traces = traces;
    }

    public record CreatedParty(PartyView party, GuestView host) {
    }

    public PingResponse ping() {
        return call(s -> s.ping(PingRequest.newBuilder().setSender("T2 app-server").build()));
    }

    public CreatedParty createParty(String joinCode, String name, String hostNickname) {
        CreatePartyResponse r = call(s -> s.createParty(CreatePartyRequest.newBuilder()
                .setJoinCode(joinCode).setName(name).setHostNickname(hostNickname).build()));
        return new CreatedParty(partyView(r.getParty()), guestView(r.getHost()));
    }

    public PartyView getPartyByJoinCode(String joinCode) {
        return partyView(call(s -> s.getPartyByJoinCode(GetPartyByJoinCodeRequest.newBuilder().setJoinCode(joinCode).build())));
    }

    public GuestView createGuest(long partyId, String nickname) {
        return guestView(call(s -> s.createGuest(CreateGuestRequest.newBuilder()
                .setPartyId(partyId).setNickname(nickname).setRole(GuestRole.GUEST).build())));
    }

    public GuestView getGuest(String guestId) {
        return guestView(call(s -> s.getGuest(GetGuestRequest.newBuilder().setGuestId(guestId).build())));
    }

    public QueueItemView addQueueItem(long partyId, String trackId, String title, String artist, String album,
                                      int durationMs, String addedByGuestId) {
        return itemView(call(s -> s.addQueueItem(AddQueueItemRequest.newBuilder()
                .setPartyId(partyId).setSpotifyTrackId(trackId).setTitle(title).setArtist(artist)
                .setAlbum(album).setDurationMs(durationMs).setAddedByGuestId(addedByGuestId).build())));
    }

    public List<QueueItemView> listQueueItems(long partyId) {
        return call(s -> s.listQueueItems(ListQueueItemsRequest.newBuilder().setPartyId(partyId).build()))
                .getItemsList().stream().map(DataClient::itemView).toList();
    }

    public VoteView upsertVote(long queueItemId, String guestId, int value) {
        return voteView(call(s -> s.upsertVote(UpsertVoteRequest.newBuilder()
                .setQueueItemId(queueItemId).setGuestId(guestId).setValue(value).build())));
    }

    public boolean deleteVote(long queueItemId, String guestId) {
        return call(s -> s.deleteVote(DeleteVoteRequest.newBuilder()
                .setQueueItemId(queueItemId).setGuestId(guestId).build())).getDeleted();
    }

    public List<VoteView> listVotes(long partyId) {
        return call(s -> s.listVotes(ListVotesRequest.newBuilder().setPartyId(partyId).build()))
                .getVotesList().stream().map(DataClient::voteView).toList();
    }

    // --- helpers ---

    private <T> T call(Function<PartayDataServiceGrpc.PartayDataServiceBlockingStub, T> rpc) {
        try {
            // Deadline per call, never on the shared stub.
            return rpc.apply(stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS));
        } catch (StatusRuntimeException e) {
            String detail = e.getStatus().getDescription() == null ? e.getStatus().getCode().name() : e.getStatus().getDescription();
            throw switch (e.getStatus().getCode()) {
                case NOT_FOUND -> new NotFoundException(detail);
                case ALREADY_EXISTS -> new ConflictException(detail);
                case INVALID_ARGUMENT -> new BadRequestException(detail);
                case UNAVAILABLE, DEADLINE_EXCEEDED -> new UpstreamException(503, "data-server unavailable");
                default -> new UpstreamException(502, "data-server error: " + detail);
            };
        }
    }

    private static PartyView partyView(Party p) {
        return new PartyView(p.getId(), p.getJoinCode(), p.getName(), Instant.ofEpochMilli(p.getCreatedAtMs()));
    }

    private GuestView guestView(Guest g) {
        traces.rememberGuest(g.getId(), g.getNickname());
        return new GuestView(g.getId(), g.getPartyId(), g.getNickname(), g.getRole().name(),
                Instant.ofEpochMilli(g.getJoinedAtMs()));
    }

    private static QueueItemView itemView(QueueItem q) {
        return new QueueItemView(q.getId(), q.getPartyId(), q.getSpotifyTrackId(), q.getTitle(), q.getArtist(),
                q.getAlbum(), q.getDurationMs(), q.getAddedByGuestId(), q.getAddedByNickname(),
                Instant.ofEpochMilli(q.getAddedAtMs()));
    }

    private static VoteView voteView(Vote v) {
        return new VoteView(v.getQueueItemId(), v.getGuestId(), v.getValue(), Instant.ofEpochMilli(v.getUpdatedAtMs()));
    }
}
