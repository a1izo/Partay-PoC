package dk.via.partay.app.service;

import dk.via.partay.app.model.QueueItemView;
import dk.via.partay.app.model.RankedItem;
import dk.via.partay.app.model.VoteView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueRankerTest {

    private static final Instant T0 = Instant.parse("2026-10-28T09:00:00Z");
    private static final String SOFIA = "11111111-1111-1111-1111-111111111111";
    private static final String JONAS = "22222222-2222-2222-2222-222222222222";
    private static final String MIKKEL = "33333333-3333-3333-3333-333333333333";

    private static QueueItemView item(long id, int secondsAfterStart) {
        return new QueueItemView(id, 1, "track" + id, "Title " + id, "Artist", "Album", 200000,
                SOFIA, "Sofia", T0.plusSeconds(secondsAfterStart));
    }

    private static VoteView vote(long itemId, String guestId, int value) {
        return new VoteView(itemId, guestId, value, T0);
    }

    private static List<Long> ids(List<RankedItem> ranked) {
        return ranked.stream().map(r -> r.item().id()).toList();
    }

    @Test
    void ordersByScoreDescending() {
        List<RankedItem> ranked = QueueRanker.rank(
                List.of(item(1, 0), item(2, 1), item(3, 2)),
                List.of(vote(3, SOFIA, 1), vote(3, JONAS, 1), vote(1, SOFIA, -1)),
                null);
        assertEquals(List.of(3L, 2L, 1L), ids(ranked));
        assertEquals(List.of(2, 0, -1), ranked.stream().map(RankedItem::score).toList());
    }

    @Test
    void tiesAreBrokenByTimeAdded() {
        List<RankedItem> ranked = QueueRanker.rank(List.of(item(1, 30), item(2, 10), item(3, 20)), List.of(), null);
        assertEquals(List.of(2L, 3L, 1L), ids(ranked));
    }

    @Test
    void tiesOnScoreAndTimeAreBrokenById() {
        List<RankedItem> ranked = QueueRanker.rank(List.of(item(7, 5), item(4, 5), item(5, 5)), List.of(), null);
        assertEquals(List.of(4L, 5L, 7L), ids(ranked));
    }

    @Test
    void positionsStartAtOne() {
        List<RankedItem> ranked = QueueRanker.rank(List.of(item(1, 0), item(2, 1)), List.of(), null);
        assertEquals(List.of(1, 2), ranked.stream().map(RankedItem::position).toList());
    }

    @Test
    void countsUpvotesAndDownvotes() {
        RankedItem r = QueueRanker.rank(List.of(item(1, 0)),
                List.of(vote(1, SOFIA, 1), vote(1, JONAS, 1), vote(1, MIKKEL, -1)), null).get(0);
        assertEquals(2, r.upvotes());
        assertEquals(1, r.downvotes());
        assertEquals(1, r.score());
    }

    @Test
    void myVoteIsTheRequestingGuestsVote() {
        List<VoteView> votes = List.of(vote(1, SOFIA, 1), vote(2, SOFIA, -1), vote(2, JONAS, 1));
        List<RankedItem> ranked = QueueRanker.rank(List.of(item(1, 0), item(2, 1), item(3, 2)), votes, SOFIA);
        assertEquals(1, find(ranked, 1).myVote());
        assertEquals(-1, find(ranked, 2).myVote());
        assertEquals(0, find(ranked, 3).myVote());
    }

    @Test
    void myVoteIsZeroWithoutGuest() {
        List<RankedItem> ranked = QueueRanker.rank(List.of(item(1, 0)), List.of(vote(1, SOFIA, 1)), null);
        assertEquals(0, ranked.get(0).myVote());
    }

    @Test
    void emptyQueue() {
        assertTrue(QueueRanker.rank(List.of(), List.of(), SOFIA).isEmpty());
    }

    private static RankedItem find(List<RankedItem> ranked, long id) {
        return ranked.stream().filter(r -> r.item().id() == id).findFirst().orElseThrow();
    }
}
