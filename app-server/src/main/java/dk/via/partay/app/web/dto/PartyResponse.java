package dk.via.partay.app.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** {@code host} is only set in the response to creating a party. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartyResponse(long partyId, String joinCode, String name, Instant createdAt, GuestSummary host) {
}
