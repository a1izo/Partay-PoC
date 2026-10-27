package dk.via.partay.app.web;

import dk.via.partay.app.service.SearchService;
import dk.via.partay.app.web.dto.SearchResponse;
import dk.via.partay.app.web.dto.TrackResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@Tag(name = "Search", description = "Live Spotify search, cached for 60 s")
public class SearchController {

    private final SearchService search;

    public SearchController(SearchService search) {
        this.search = search;
    }

    @GetMapping
    @Operation(summary = "Search tracks", description = "q: 2 to 100 characters. limit: 1 to 10, default 5.")
    public SearchResponse search(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) Integer limit) {
        SearchService.Result r = search.search(q, limit);
        return new SearchResponse(r.query(), r.cached(), r.tookMs(), r.tracks().stream()
                .map(t -> new TrackResponse(t.spotifyTrackId(), t.title(), t.artist(), t.album(), t.durationMs(),
                        t.previewImageUrl()))
                .toList());
    }
}
