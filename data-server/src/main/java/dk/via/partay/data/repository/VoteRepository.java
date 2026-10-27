package dk.via.partay.data.repository;

import dk.via.partay.data.entity.VoteEntity;
import dk.via.partay.data.entity.VoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoteRepository extends JpaRepository<VoteEntity, VoteId> {

    @Query("select v from VoteEntity v, QueueItemEntity q where q.id = v.id.queueItemId and q.partyId = :partyId")
    List<VoteEntity> findByPartyId(@Param("partyId") long partyId);

    // Race-safe: one statement, the primary key (queue_item_id, guest_id) decides insert or update.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO vote (queue_item_id, guest_id, vote_value) VALUES (:itemId, :guestId, :value) AS new
            ON DUPLICATE KEY UPDATE vote_value = new.vote_value, updated_at = CURRENT_TIMESTAMP(3)
            """, nativeQuery = true)
    int upsert(@Param("itemId") long itemId, @Param("guestId") String guestId, @Param("value") short value);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from VoteEntity v where v.id.queueItemId = :itemId and v.id.guestId = :guestId")
    int deleteVote(@Param("itemId") long itemId, @Param("guestId") String guestId);
}
