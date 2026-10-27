package dk.via.partay.data.grpc;

import dk.via.partay.data.entity.GuestEntity;
import dk.via.partay.data.entity.PartyEntity;
import dk.via.partay.data.entity.QueueItemEntity;
import dk.via.partay.data.entity.VoteId;
import dk.via.partay.data.repository.GuestRepository;
import dk.via.partay.data.repository.PartyRepository;
import dk.via.partay.data.repository.QueueItemRepository;
import dk.via.partay.data.repository.VoteRepository;
import dk.via.partay.grpc.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * T3 gRPC service. Stores and returns rows, no business rules.
 * <p>
 * Each call runs in its own transaction (read-only where it only reads). A {@link TransactionTemplate}
 * is used instead of {@code @Transactional} so the commit happens before {@code onNext}: the client
 * never sees a success for a row that later fails to commit, and constraint errors raised at commit
 * are still mapped to a gRPC status.
 */
@Service
public class PartayDataGrpcService extends PartayDataServiceGrpc.PartayDataServiceImplBase {

    private final PartyRepository parties;
    private final GuestRepository guests;
    private final QueueItemRepository queueItems;
    private final VoteRepository votes;
    private final DataSource dataSource;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public PartayDataGrpcService(PartyRepository parties, GuestRepository guests, QueueItemRepository queueItems,
                                 VoteRepository votes, DataSource dataSource, PlatformTransactionManager txManager) {
        this.parties = parties;
        this.guests = guests;
        this.queueItems = queueItems;
        this.votes = votes;
        this.dataSource = dataSource;
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> observer) {
        boolean dbOk;
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT 1")) {
            dbOk = rs.next() && rs.getInt(1) == 1;
        } catch (Exception e) {
            dbOk = false;
        }
        observer.onNext(PingResponse.newBuilder()
                .setMessage("pong from T3 data-server" + (request.getSender().isBlank() ? "" : " to " + request.getSender()))
                .setServerTimeMs(System.currentTimeMillis())
                .setDatabaseOk(dbOk)
                .build());
        observer.onCompleted();
    }

    @Override
    public void createParty(CreatePartyRequest request, StreamObserver<CreatePartyResponse> observer) {
        respond(observer, tx, () -> {
            require(request.getJoinCode(), "join_code");
            require(request.getName(), "name");
            require(request.getHostNickname(), "host_nickname");
            Instant now = now();
            PartyEntity party = parties.saveAndFlush(new PartyEntity(request.getJoinCode(), request.getName(), now));
            GuestEntity host = guests.saveAndFlush(new GuestEntity(UUID.randomUUID().toString(), party.getId(),
                    request.getHostNickname(), GuestEntity.Role.HOST, now));
            return CreatePartyResponse.newBuilder()
                    .setParty(ProtoMapper.toProto(party))
                    .setHost(ProtoMapper.toProto(host))
                    .build();
        });
    }

    @Override
    public void getPartyByJoinCode(GetPartyByJoinCodeRequest request, StreamObserver<Party> observer) {
        respond(observer, readTx, () -> parties.findByJoinCode(request.getJoinCode())
                .map(ProtoMapper::toProto)
                .orElseThrow(() -> notFound("party " + request.getJoinCode() + " not found")));
    }

    @Override
    public void createGuest(CreateGuestRequest request, StreamObserver<Guest> observer) {
        respond(observer, tx, () -> {
            require(request.getNickname(), "nickname");
            GuestEntity.Role role = ProtoMapper.fromProto(request.getRole());
            if (!parties.existsById(request.getPartyId())) {
                throw notFound("party " + request.getPartyId() + " not found");
            }
            GuestEntity guest = guests.saveAndFlush(new GuestEntity(UUID.randomUUID().toString(),
                    request.getPartyId(), request.getNickname(), role, now()));
            return ProtoMapper.toProto(guest);
        });
    }

    @Override
    public void getGuest(GetGuestRequest request, StreamObserver<Guest> observer) {
        respond(observer, readTx, () -> guests.findById(request.getGuestId())
                .map(ProtoMapper::toProto)
                .orElseThrow(() -> notFound("guest " + request.getGuestId() + " not found")));
    }

    @Override
    public void addQueueItem(AddQueueItemRequest request, StreamObserver<QueueItem> observer) {
        respond(observer, tx, () -> {
            require(request.getSpotifyTrackId(), "spotify_track_id");
            require(request.getTitle(), "title");
            require(request.getArtist(), "artist");
            if (!parties.existsById(request.getPartyId())) {
                throw notFound("party " + request.getPartyId() + " not found");
            }
            GuestEntity addedBy = guests.findById(request.getAddedByGuestId())
                    .orElseThrow(() -> notFound("guest " + request.getAddedByGuestId() + " not found"));
            QueueItemEntity item = queueItems.saveAndFlush(new QueueItemEntity(request.getPartyId(),
                    request.getSpotifyTrackId(), request.getTitle(), request.getArtist(), request.getAlbum(),
                    request.getDurationMs(), addedBy, now()));
            return ProtoMapper.toProto(item);
        });
    }

    @Override
    public void listQueueItems(ListQueueItemsRequest request, StreamObserver<ListQueueItemsResponse> observer) {
        respond(observer, readTx, () -> ListQueueItemsResponse.newBuilder()
                .addAllItems(queueItems.findByPartyIdOrderByIdAsc(request.getPartyId()).stream()
                        .map(ProtoMapper::toProto).toList())
                .build());
    }

    @Override
    public void upsertVote(UpsertVoteRequest request, StreamObserver<Vote> observer) {
        respond(observer, tx, () -> {
            if (request.getValue() != 1 && request.getValue() != -1) {
                throw invalid("vote value must be -1 or 1, got " + request.getValue());
            }
            if (!queueItems.existsById(request.getQueueItemId())) {
                throw notFound("queue item " + request.getQueueItemId() + " not found");
            }
            if (!guests.existsById(request.getGuestId())) {
                throw notFound("guest " + request.getGuestId() + " not found");
            }
            votes.upsert(request.getQueueItemId(), request.getGuestId(), (short) request.getValue());
            return votes.findById(new VoteId(request.getQueueItemId(), request.getGuestId()))
                    .map(ProtoMapper::toProto)
                    .orElseThrow(() -> Status.INTERNAL.withDescription("vote missing after upsert").asRuntimeException());
        });
    }

    @Override
    public void deleteVote(DeleteVoteRequest request, StreamObserver<DeleteVoteResponse> observer) {
        respond(observer, tx, () -> DeleteVoteResponse.newBuilder()
                .setDeleted(votes.deleteVote(request.getQueueItemId(), request.getGuestId()) > 0)
                .build());
    }

    @Override
    public void listVotes(ListVotesRequest request, StreamObserver<ListVotesResponse> observer) {
        respond(observer, readTx, () -> ListVotesResponse.newBuilder()
                .addAllVotes(votes.findByPartyId(request.getPartyId()).stream().map(ProtoMapper::toProto).toList())
                .build());
    }

    // --- helpers ---

    private static <T> void respond(StreamObserver<T> observer, TransactionTemplate template, Supplier<T> work) {
        T result;
        try {
            result = template.execute(status -> work.get());
        } catch (StatusRuntimeException e) {
            observer.onError(e);
            return;
        } catch (DataIntegrityViolationException e) {
            String detail = e.getMostSpecificCause().getMessage();
            Status status = detail != null && detail.startsWith("Duplicate entry") ? Status.ALREADY_EXISTS : Status.INVALID_ARGUMENT;
            observer.onError(status.withDescription(detail).asRuntimeException());
            return;
        } catch (IllegalArgumentException e) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
            return;
        } catch (RuntimeException e) {
            observer.onError(Status.INTERNAL.withDescription(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .asRuntimeException());
            return;
        }
        observer.onNext(result);
        observer.onCompleted();
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " is required");
        }
    }

    private static StatusRuntimeException notFound(String message) {
        return Status.NOT_FOUND.withDescription(message).asRuntimeException();
    }

    private static StatusRuntimeException invalid(String message) {
        return Status.INVALID_ARGUMENT.withDescription(message).asRuntimeException();
    }

    // MySQL TIMESTAMP(3) keeps milliseconds, so cut the rest off in Java to return exactly what is stored.
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
