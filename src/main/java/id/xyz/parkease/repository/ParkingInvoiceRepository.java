package id.xyz.parkease.repository;

import id.xyz.parkease.domain.ParkingInvoice;
import id.xyz.parkease.domain.ParkingInvoice.PaymentStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ParkingInvoiceRepository extends JpaRepository<ParkingInvoice, UUID> {

    Optional<ParkingInvoice> findByReservation_Id(UUID reservationId);

    List<ParkingInvoice> findByPaymentStatusAndGeneratedAtBefore(PaymentStatus paymentStatus, OffsetDateTime cutoff);

    // Guarded transition: only a currently PAYMENT_PENDING invoice can move to
    // PAID, so a capture cannot overwrite an already-expired invoice.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ParkingInvoice i SET i.paymentStatus = id.xyz.parkease.domain.ParkingInvoice.PaymentStatus.PAID, "
            + "i.paidAt = :paidAt WHERE i.id = :id AND i.paymentStatus = id.xyz.parkease.domain.ParkingInvoice.PaymentStatus.PAYMENT_PENDING")
    int markPaidIfPending(@Param("id") UUID id, @Param("paidAt") OffsetDateTime paidAt);

    // Guarded transition: only a PAYMENT_PENDING invoice can expire, so a timeout
    // cannot overwrite an invoice that was concurrently paid.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ParkingInvoice i SET i.paymentStatus = id.xyz.parkease.domain.ParkingInvoice.PaymentStatus.EXPIRED "
            + "WHERE i.id = :id AND i.paymentStatus = id.xyz.parkease.domain.ParkingInvoice.PaymentStatus.PAYMENT_PENDING")
    int markExpiredIfPending(@Param("id") UUID id);
}
