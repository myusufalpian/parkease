package id.xyz.parkease.dto;

public record BookingResponse(ReservationResponse reservation, InvoiceResponse invoice) {
}
