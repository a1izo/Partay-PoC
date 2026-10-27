package dk.via.partay.app.service;

import dk.via.partay.app.model.QueueItemView;
import dk.via.partay.app.model.RankedItem;
import dk.via.partay.app.model.VoteView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranks the queue: score (sum of votes) descending, then time added ascending, then id ascending.
 * Pure Java, no Spring.
 */
public final class QueueRanker {

    private QueueRanker() {
    }

    /** @param guestId the requesting guest for {@code myVote}, or null */
    public static List<RankedItem> rank(List<QueueItemView> items, List<VoteView> votes, String guestId) {
        Map<Long, int[]> tally = new HashMap<>(); // itemId -> {upvotes, downvotes, myVote}
        for (VoteView v : votes) {
            int[] t = tally.computeIfAbsent(v.queueItemId(), id -> new int[3]);
            if (v.value() > 0) {
                t[0]++;
            } else if (v.value() < 0) {
                t[1]++;
            }
            if (guestId != null && guestId.equalsIgnoreCase(v.guestId())) {
                t[2] = Integer.signum(v.value());
            }
        }

        record Scored(QueueItemView item, int up, int down, int mine) {
            int score() {
                return up - down;
            }
        }
        List<Scored> scored = new ArrayList<>();
        for (QueueItemView item : items) {
            int[] t = tally.getOrDefault(item.id(), new int[3]);
            scored.add(new Scored(item, t[0], t[1], t[2]));
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed()
                .thenComparing(s -> s.item().addedAt())
                .thenComparingLong(s -> s.item().id()));

        List<RankedItem> ranked = new ArrayList<>(scored.size());
        for (int i = 0; i < scored.size(); i++) {
            Scored s = scored.get(i);
            ranked.add(new RankedItem(i + 1, s.item(), s.score(), s.up(), s.down(), s.mine()));
        }
        return ranked;
    }
}
