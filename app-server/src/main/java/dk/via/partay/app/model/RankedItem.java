package dk.via.partay.app.model;

/** A queue item with its place in the ranked queue. {@code myVote} is -1, 0 or 1 for the requesting guest. */
public record RankedItem(int position, QueueItemView item, int score, int upvotes, int downvotes, int myVote) {
}
