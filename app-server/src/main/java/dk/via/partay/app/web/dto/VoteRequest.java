package dk.via.partay.app.web.dto;

import jakarta.validation.constraints.NotNull;

/** @param value 1 = up, -1 = down, 0 = clear */
public record VoteRequest(@NotNull Integer value) {
}
