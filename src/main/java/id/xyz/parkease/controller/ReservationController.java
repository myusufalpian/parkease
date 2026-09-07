package id.xyz.parkease.controller;

import id.xyz.parkease.dto.AvailabilityResponse;
import id.xyz.parkease.dto.CancelReservationRequest;
import id.xyz.parkease.dto.ExtendReservationRequest;
import id.xyz.parkease.dto.ReservationRequest;
import id.xyz.parkease.dto.ReservationResponse;
import id.xyz.parkease.service.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.createReservation(request));
    }

    @PostMapping("/reservations/{reservationId}/check-in")
    public ReservationResponse checkIn(@PathVariable UUID reservationId) {
        return reservationService.checkIn(reservationId);
    }

    @PostMapping("/reservations/{reservationId}/check-out")
    public ReservationResponse checkOut(@PathVariable UUID reservationId) {
        return reservationService.checkOut(reservationId);
    }

    @DeleteMapping("/reservations/{reservationId}")
    public ReservationResponse cancel(
            @PathVariable UUID reservationId,
            @RequestBody(required = false) @Valid CancelReservationRequest request) {
        String reason = request == null ? null : request.reason();
        return reservationService.cancel(reservationId, reason);
    }

    @PutMapping("/reservations/{reservationId}/extend")
    public ReservationResponse extend(
            @PathVariable UUID reservationId,
            @Valid @RequestBody ExtendReservationRequest request) {
        return reservationService.extend(reservationId, request.plannedEnd());
    }

    @GetMapping("/lots/{lotId}/availability")
    public AvailabilityResponse availability(
            @PathVariable UUID lotId,
            @RequestParam("start") @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime plannedStart,
            @RequestParam("end") @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime plannedEnd,
            @RequestParam(value = "vehicleType", required = false) String vehicleType) {
        return reservationService.getAvailability(lotId, plannedStart, plannedEnd, vehicleType);
    }
}
