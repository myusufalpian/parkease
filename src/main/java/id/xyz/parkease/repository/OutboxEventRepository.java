package id.xyz.parkease.repository;

import id.xyz.parkease.domain.OutboxEvent;
import id.xyz.parkease.domain.OutboxEvent.OutboxStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByAggregateIdAndStatus(UUID aggregateId, OutboxStatus status);

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
