package dk.via.partay.data.grpc;

import dk.via.partay.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T3 against a real MySQL 8.4 (Testcontainers) through an in-process gRPC channel.
 * Skipped when Docker is not available.
 */
@SpringBootTest(properties = "grpc.server.port=0")
@Testcontainers(disabledWithoutDocker = true)
class PartayDataGrpcServiceIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private PartayDataGrpcService service;

    @Autowired
    private JdbcTemplate jdbc;

    private Server server;
    private ManagedChannel channel;
    private PartayDataServiceGrpc.PartayDataServiceBlockingStub stub;

    @BeforeEach
    void startInProcess() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(service).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        stub = PartayDataServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void stopInProcess() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    private static String newCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private CreatePartyResponse createParty(String code) {
        return stub.createParty(CreatePartyRequest.newBuilder()
                .setJoinCode(code).setName("Friday pre-drinks").setHostNickname("Mikkel").build());
    }

    @Test
    void pingReportsDatabase() {
        assertTrue(stub.ping(PingRequest.newBuilder().setSender("test").build()).getDatabaseOk());
    }

    @Test
    void createPartyStoresPartyAndHost() {
        String code = newCode();
        CreatePartyResponse r = createParty(code);

        assertEquals(code, r.getParty().getJoinCode());
        assertEquals(GuestRole.HOST, r.getHost().getRole());
        assertEquals(r.getParty().getId(), r.getHost().getPartyId());
        assertEquals(r.getParty().getId(),
                stub.getPartyByJoinCode(GetPartyByJoinCodeRequest.newBuilder().setJoinCode(code).build()).getId());
        assertEquals("Mikkel", stub.getGuest(GetGuestRequest.newBuilder().setGuestId(r.getHost().getId()).build()).getNickname());
    }

    @Test
    void duplicateJoinCodeIsAlreadyExists() {
        String code = newCode();
        createParty(code);
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> createParty(code));
        assertEquals(Status.Code.ALREADY_EXISTS, e.getStatus().getCode());
    }

    @Test
    void unknownPartyIsNotFound() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> stub.getPartyByJoinCode(GetPartyByJoinCodeRequest.newBuilder().setJoinCode("ZZZZZZ").build()));
        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
    }

    @Test
    void upsertVoteTwiceKeepsOneRowWithTheLatestValue() {
        CreatePartyResponse party = createParty(newCode());
        QueueItem item = addItem(party);
        String guestId = party.getHost().getId();

        stub.upsertVote(UpsertVoteRequest.newBuilder().setQueueItemId(item.getId()).setGuestId(guestId).setValue(1).build());
        Vote second = stub.upsertVote(UpsertVoteRequest.newBuilder()
                .setQueueItemId(item.getId()).setGuestId(guestId).setValue(-1).build());

        assertEquals(-1, second.getValue());
        assertEquals(1, countVotes(item.getId()));
        ListVotesResponse votes = stub.listVotes(ListVotesRequest.newBuilder().setPartyId(party.getParty().getId()).build());
        assertEquals(1, votes.getVotesCount());
        assertEquals(-1, votes.getVotes(0).getValue());
    }

    @Test
    void deleteVoteRemovesTheRow() {
        CreatePartyResponse party = createParty(newCode());
        QueueItem item = addItem(party);
        String guestId = party.getHost().getId();
        stub.upsertVote(UpsertVoteRequest.newBuilder().setQueueItemId(item.getId()).setGuestId(guestId).setValue(1).build());

        DeleteVoteRequest delete = DeleteVoteRequest.newBuilder().setQueueItemId(item.getId()).setGuestId(guestId).build();
        assertTrue(stub.deleteVote(delete).getDeleted());
        assertEquals(0, countVotes(item.getId()));
        assertFalse(stub.deleteVote(delete).getDeleted());
    }

    @Test
    void invalidVoteValueIsInvalidArgument() {
        CreatePartyResponse party = createParty(newCode());
        QueueItem item = addItem(party);
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> stub.upsertVote(
                UpsertVoteRequest.newBuilder().setQueueItemId(item.getId()).setGuestId(party.getHost().getId()).setValue(5).build()));
        assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
    }

    private QueueItem addItem(CreatePartyResponse party) {
        QueueItem item = stub.addQueueItem(AddQueueItemRequest.newBuilder()
                .setPartyId(party.getParty().getId()).setSpotifyTrackId("0VjIjW4GlUZAMYd2vXMi3b")
                .setTitle("Blinding Lights").setArtist("The Weeknd").setAlbum("After Hours").setDurationMs(200040)
                .setAddedByGuestId(party.getHost().getId()).build());
        assertEquals("Mikkel", item.getAddedByNickname());
        return item;
    }

    private int countVotes(long itemId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM vote WHERE queue_item_id = ?", Integer.class, itemId);
        return n == null ? 0 : n;
    }
}
