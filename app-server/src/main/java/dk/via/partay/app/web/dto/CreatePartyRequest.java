package dk.via.partay.app.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePartyRequest(@NotBlank String name, @NotBlank String hostNickname) {
}
