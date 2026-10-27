package dk.via.partay.data.grpc;

import dk.via.partay.data.entity.GuestEntity;
import dk.via.partay.data.entity.PartyEntity;
import dk.via.partay.data.entity.QueueItemEntity;
import dk.via.partay.data.entity.VoteEntity;
import dk.via.partay.grpc.Guest;
import dk.via.partay.grpc.GuestRole;
import dk.via.partay.grpc.Party;
import dk.via.partay.grpc.QueueItem;
import dk.via.partay.grpc.Vote;

/** Entity to proto conversion. Timestamps travel as epoch milliseconds. */
public final class ProtoMapper {

    private ProtoMapper() {
    }

    public static Party toProto(PartyEntity e) {
        return Party.newBuilder()
                .setId(e.getId())
                .setJoinCode(e.getJoinCode())
                .setName(e.getName())
                .setCreatedAtMs(e.getCreatedAt().toEpochMilli())
                .build();
    }

    public static Guest toProto(GuestEntity e) {
        return Guest.newBuilder()
                .setId(e.getId())
                .setPartyId(e.getPartyId())
                .setNickname(e.getNickname())
                .setRole(toProto(e.getRole()))
                .setJoinedAtMs(e.getJoinedAt().toEpochMilli())
                .build();
    }

    public static QueueItem toProto(QueueItemEntity e) {
        return QueueItem.newBuilder()
                .setId(e.getId())
                .setPartyId(e.getPartyId())
                .setSpotifyTrackId(e.getSpotifyTrackId())
                .setTitle(e.getTitle())
                .setArtist(e.getArtist())
                .setAlbum(e.getAlbum())
                .setDurationMs(e.getDurationMs())
                .setAddedByGuestId(e.getAddedBy().getId())
                .setAddedByNickname(e.getAddedBy().getNickname())
                .setAddedAtMs(e.getAddedAt().toEpochMilli())
                .build();
    }

    public static Vote toProto(VoteEntity e) {
        return Vote.newBuilder()
                .setQueueItemId(e.getId().getQueueItemId())
                .setGuestId(e.getId().getGuestId())
                .setValue(e.getValue())
                .setUpdatedAtMs(e.getUpdatedAt().toEpochMilli())
                .build();
    }

    public static GuestRole toProto(GuestEntity.Role role) {
        return role == GuestEntity.Role.HOST ? GuestRole.HOST : GuestRole.GUEST;
    }

    public static GuestEntity.Role fromProto(GuestRole role) {
        return switch (role) {
            case HOST -> GuestEntity.Role.HOST;
            case GUEST -> GuestEntity.Role.GUEST;
            default -> throw new IllegalArgumentException("role is required");
        };
    }
}
