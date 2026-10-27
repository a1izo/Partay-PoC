package dk.via.partay.data.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "party")
public class PartyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "join_code", nullable = false, length = 6)
    private String joinCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PartyEntity() {
    }

    public PartyEntity(String joinCode, String name, Instant createdAt) {
        this.joinCode = joinCode;
        this.name = name;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getJoinCode() { return joinCode; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
}
