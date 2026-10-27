package dk.via.partay.data.entity;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Entity
@Table(name = "guest")
public class GuestEntity implements Persistable<String> {

    public enum Role { HOST, GUEST }

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "party_id", nullable = false)
    private long partyId;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    // The id is assigned in Java, so tell Spring Data to INSERT (persist) instead of SELECT + merge.
    @Transient
    private boolean isNew = true;

    protected GuestEntity() {
    }

    public GuestEntity(String id, long partyId, String nickname, Role role, Instant joinedAt) {
        this.id = id;
        this.partyId = partyId;
        this.nickname = nickname;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    @Override
    public String getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    public long getPartyId() { return partyId; }
    public String getNickname() { return nickname; }
    public Role getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
}
