package dk.via.partay.app.web.dto;

import java.util.List;

public record QueueResponse(String joinCode, String partyName, List<QueueEntryResponse> items) {
}
