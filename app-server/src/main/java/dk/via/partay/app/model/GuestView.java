package dk.via.partay.app.model;

import java.time.Instant;

/** @param role HOST or GUEST */
public record GuestView(String id, long partyId, String nickname, String role, Instant joinedAt) {
}
