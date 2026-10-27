package dk.via.partay.data.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "vote")
public class VoteEntity {

    @EmbeddedId
    private VoteId id;

    @Column(name = "vote_value", nullable = false)
    private short value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VoteEntity() {
    }

    public VoteId getId() { return id; }
    public short getValue() { return value; }
    public Instant getUpdatedAt() { return updatedAt; }
}
