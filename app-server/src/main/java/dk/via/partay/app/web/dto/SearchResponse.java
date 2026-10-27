package dk.via.partay.app.web.dto;

import java.util.List;

public record SearchResponse(String query, boolean cached, long tookMs, List<TrackResponse> tracks) {
}
