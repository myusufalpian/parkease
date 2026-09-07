package id.xyz.parkease.repository;

import id.xyz.parkease.domain.Refund;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findByInvoice_Id(UUID invoiceId);

    Optional<Refund> findByPaymentTransactionId(String paymentTransactionId);
}
