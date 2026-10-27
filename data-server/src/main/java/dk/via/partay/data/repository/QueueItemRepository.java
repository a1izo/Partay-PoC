package dk.via.partay.data.repository;

import dk.via.partay.data.entity.QueueItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QueueItemRepository extends JpaRepository<QueueItemEntity, Long> {

    // Fetch join on addedBy so the nickname comes in the same SELECT (no N+1).
    @Query("select q from QueueItemEntity q join fetch q.addedBy where q.partyId = :partyId order by q.id asc")
    List<QueueItemEntity> findByPartyIdOrderByIdAsc(@Param("partyId") long partyId);
}
