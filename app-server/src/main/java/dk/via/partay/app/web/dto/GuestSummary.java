package dk.via.partay.app.web.dto;

/** @param role HOST or GUEST */
public record GuestSummary(String guestId, String nickname, String role) {
}
