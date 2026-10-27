package dk.via.partay.app.web.dto;

public record JoinPartyResponse(String guestId, String nickname, String role, long partyId, String joinCode) {
}
