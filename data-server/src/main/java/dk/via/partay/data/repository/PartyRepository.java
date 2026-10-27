package dk.via.partay.data.repository;

import dk.via.partay.data.entity.PartyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PartyRepository extends JpaRepository<PartyEntity, Long> {
    Optional<PartyEntity> findByJoinCode(String joinCode);
}
