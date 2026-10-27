package dk.via.partay.app.web;

import dk.via.partay.app.grpc.DataClient;
import dk.via.partay.app.model.GuestView;
import dk.via.partay.app.model.PartyView;
import dk.via.partay.app.service.PartyService;
import dk.via.partay.app.web.dto.CreatePartyRequest;
import dk.via.partay.app.web.dto.GuestSummary;
import dk.via.partay.app.web.dto.JoinPartyRequest;
import dk.via.partay.app.web.dto.JoinPartyResponse;
import dk.via.partay.app.web.dto.PartyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parties")
@Tag(name = "Parties", description = "Create a party and join it")
public class PartyController {

    private final PartyService parties;

    public PartyController(PartyService parties) {
        this.parties = parties;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a party", description = "The host gets a 6-character join code and a guestId.")
    public PartyResponse create(@Valid @RequestBody CreatePartyRequest request) {
        DataClient.CreatedParty created = parties.createParty(request.name(), request.hostNickname());
        PartyView p = created.party();
        GuestView host = created.host();
        return new PartyResponse(p.id(), p.joinCode(), p.name(), p.createdAt(),
                new GuestSummary(host.id(), host.nickname(), host.role()));
    }

    @GetMapping("/{joinCode}")
    @Operation(summary = "Look up a party by join code", description = "Join codes are case-insensitive.")
    public PartyResponse get(@PathVariable String joinCode) {
        PartyView p = parties.getParty(joinCode);
        return new PartyResponse(p.id(), p.joinCode(), p.name(), p.createdAt(), null);
    }

    @PostMapping("/{joinCode}/guests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Join a party with a nickname", description = "Returns the guestId to send as X-Guest-Id.")
    public JoinPartyResponse join(@PathVariable String joinCode, @Valid @RequestBody JoinPartyRequest request) {
        PartyService.Member m = parties.join(joinCode, request.nickname());
        return new JoinPartyResponse(m.guest().id(), m.guest().nickname(), m.guest().role(), m.party().id(),
                m.party().joinCode());
    }
}
