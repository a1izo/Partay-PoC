package dk.via.partay.data.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "queue_item")
public class QueueItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "party_id", nullable = false)
    private long partyId;

    @Column(name = "spotify_track_id", nullable = false, length = 64)
    private String spotifyTrackId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 300)
    private String artist;

    @Column(nullable = false, length = 300)
    private String album;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "added_by", nullable = false)
    private GuestEntity addedBy;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected QueueItemEntity() {
    }

    public QueueItemEntity(long partyId, String spotifyTrackId, String title, String artist, String album,
                           int durationMs, GuestEntity addedBy, Instant addedAt) {
        this.partyId = partyId;
        this.spotifyTrackId = spotifyTrackId;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.durationMs = durationMs;
        this.addedBy = addedBy;
        this.addedAt = addedAt;
    }

    public Long getId() { return id; }
    public long getPartyId() { return partyId; }
    public String getSpotifyTrackId() { return spotifyTrackId; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public int getDurationMs() { return durationMs; }
    public GuestEntity getAddedBy() { return addedBy; }
    public Instant getAddedAt() { return addedAt; }
}
