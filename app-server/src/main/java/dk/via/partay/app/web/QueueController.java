package dk.via.partay.app.web;

import dk.via.partay.app.model.QueueItemView;
import dk.via.partay.app.model.RankedItem;
import dk.via.partay.app.service.QueueService;
import dk.via.partay.app.web.dto.AddSongRequest;
import dk.via.partay.app.web.dto.QueueEntryResponse;
import dk.via.partay.app.web.dto.QueueItemAddedResponse;
import dk.via.partay.app.web.dto.QueueResponse;
import dk.via.partay.app.web.dto.VoteRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parties/{joinCode}/queue")
@Tag(name = "Queue", description = "Add songs, vote, read the ranked queue")
public class QueueController {

    static final String GUEST_HEADER = "X-Guest-Id";

    private final QueueService queue;

    public QueueController(QueueService queue) {
        this.queue = queue;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a song by Spotify track ID",
            description = "T2 fetches title, artist and duration from Spotify. The client sends only the ID.")
    public QueueItemAddedResponse add(
            @PathVariable String joinCode,
            @Parameter(in = ParameterIn.HEADER, name = GUEST_HEADER, required = true, description = "guestId from join")
            @RequestHeader(GUEST_HEADER) String guestId,
            @Valid @RequestBody AddSongRequest request) {
        QueueItemView i = queue.addSong(joinCode, guestId, request.spotifyTrackId());
        return new QueueItemAddedResponse(i.id(), i.spotifyTrackId(), i.title(), i.artist(), i.album(),
                i.durationMs(), i.addedByNickname(), i.addedAt());
    }

    @GetMapping
    @Operation(summary = "Ranked queue", description = "Ordered by score, then time added. myVote is 0 without X-Guest-Id.")
    public QueueResponse get(
            @PathVariable String joinCode,
            @Parameter(in = ParameterIn.HEADER, name = GUEST_HEADER, description = "optional, fills myVote")
            @RequestHeader(value = GUEST_HEADER, required = false) String guestId) {
        return toResponse(queue.getQueue(joinCode, guestId));
    }

    @PutMapping("/{itemId}/vote")
    @Operation(summary = "Vote up (1), down (-1) or clear (0)",
            description = "A new vote replaces the old one. Returns the full ranked queue.")
    public QueueResponse vote(
            @PathVariable String joinCode,
            @PathVariable long itemId,
            @Parameter(in = ParameterIn.HEADER, name = GUEST_HEADER, required = true, description = "guestId from join")
            @RequestHeader(GUEST_HEADER) String guestId,
            @Valid @RequestBody VoteRequest request) {
        return toResponse(queue.vote(joinCode, guestId, itemId, request.value()));
    }

    private static QueueResponse toResponse(QueueService.Queue q) {
        return new QueueResponse(q.party().joinCode(), q.party().name(),
                q.items().stream().map(QueueController::toEntry).toList());
    }

    private static QueueEntryResponse toEntry(RankedItem r) {
        QueueItemView i = r.item();
        return new QueueEntryResponse(r.position(), i.id(), i.spotifyTrackId(), i.title(), i.artist(), i.album(),
                i.addedByNickname(), r.score(), r.upvotes(), r.downvotes(), r.myVote(), i.durationMs(), i.addedAt());
    }
}
