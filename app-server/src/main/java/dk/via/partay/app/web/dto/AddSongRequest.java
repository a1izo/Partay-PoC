package dk.via.partay.app.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Only the track ID. The server never trusts song data from a client. */
public record AddSongRequest(@NotBlank String spotifyTrackId) {
}
