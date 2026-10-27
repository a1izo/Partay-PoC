package dk.via.partay.app.model;

import java.time.Instant;

public record PartyView(long id, String joinCode, String name, Instant createdAt) {
}
