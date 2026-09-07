package id.xyz.parkease.mapper;

import id.xyz.parkease.domain.ParkingInvoice;
import id.xyz.parkease.dto.InvoiceResponse;
import org.springframework.stereotype.Component;

@Component
public class InvoiceMapper {

    public InvoiceResponse toResponse(ParkingInvoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getReservation().getId(),
                invoice.getDurationMinutes(),
                invoice.getSubtotal(),
                invoice.getDiscountAmount(),
                invoice.getTotal(),
                invoice.getCurrency(),
                invoice.getPaymentStatus(),
                invoice.getGeneratedAt(),
                invoice.getPaidAt());
    }
}
