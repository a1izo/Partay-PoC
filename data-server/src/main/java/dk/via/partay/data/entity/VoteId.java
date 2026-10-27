package dk.via.partay.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class VoteId implements Serializable {

    @Column(name = "queue_item_id", nullable = false)
    private long queueItemId;

    @Column(name = "guest_id", nullable = false, length = 36)
    private String guestId;

    protected VoteId() {
    }

    public VoteId(long queueItemId, String guestId) {
        this.queueItemId = queueItemId;
        this.guestId = guestId;
    }

    public long getQueueItemId() { return queueItemId; }
    public String getGuestId() { return guestId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VoteId other)) return false;
        return queueItemId == other.queueItemId && Objects.equals(guestId, other.guestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queueItemId, guestId);
    }
}
