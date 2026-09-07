package id.xyz.parkease.repository;

import id.xyz.parkease.domain.BillingOperation;
import id.xyz.parkease.domain.BillingOperation.OperationType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillingOperationRepository extends JpaRepository<BillingOperation, UUID> {

    Optional<BillingOperation> findByIdempotencyKey(String idempotencyKey);

    Optional<BillingOperation> findByReservation_IdAndOperationType(UUID reservationId, OperationType operationType);
}
