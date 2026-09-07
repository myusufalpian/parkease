package id.xyz.parkease.controller;

import id.xyz.parkease.dto.AvailabilityResponse;
import id.xyz.parkease.dto.BookingResponse;
import id.xyz.parkease.dto.CancelReservationRequest;
import id.xyz.parkease.dto.CancellationResponse;
import id.xyz.parkease.dto.ExtendReservationRequest;
import id.xyz.parkease.dto.ExtensionChargeResponse;
import id.xyz.parkease.dto.ReservationRequest;
import id.xyz.parkease.dto.ReservationResponse;
import id.xyz.parkease.exception.UnauthorizedException;
import id.xyz.parkease.repository.ExtensionChargeRepository;
import id.xyz.parkease.repository.ParkingInvoiceRepository;
import id.xyz.parkease.security.AuthenticatedPrincipal;
import id.xyz.parkease.security.JwtAuthenticationFilter;
import id.xyz.parkease.service.AuthorizationService;
import id.xyz.parkease.service.BillingCancellationService;
import id.xyz.parkease.service.BillingExtensionService;
import id.xyz.parkease.service.BillingReservationService;
import id.xyz.parkease.service.OutboxDispatcher;
import id.xyz.parkease.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final BillingReservationService billingReservationService;
    private final BillingCancellationService billingCancellationService;
    private final BillingExtensionService billingExtensionService;
    private final AuthorizationService authorizationService;
    private final OutboxDispatcher outboxDispatcher;
    private final ParkingInvoiceRepository parkingInvoiceRepository;
    private final ExtensionChargeRepository extensionChargeRepository;

    public ReservationController(
            ReservationService reservationService,
            BillingReservationService billingReservationService,
            BillingCancellationService billingCancellationService,
            BillingExtensionService billingExtensionService,
            AuthorizationService authorizationService,
            OutboxDispatcher outboxDispatcher,
            ParkingInvoiceRepository parkingInvoiceRepository,
            ExtensionChargeRepository extensionChargeRepository) {
        this.reservationService = reservationService;
        this.billingReservationService = billingReservationService;
        this.billingCancellationService = billingCancellationService;
        this.billingExtensionService = billingExtensionService;
        this.authorizationService = authorizationService;
        this.outboxDispatcher = outboxDispatcher;
        this.parkingInvoiceRepository = parkingInvoiceRepository;
        this.extensionChargeRepository = extensionChargeRepository;
    }

    @PostMapping("/reservations")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody ReservationRequest request, HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = requirePrincipal(servletRequest);
        BookingResponse response = billingReservationService.book(request, principal);
        outboxDispatcher.dispatchPendingFor(response.reservation().id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/reservations/{reservationId}/check-in")
    public ReservationResponse checkIn(@PathVariable UUID reservationId, HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = requirePrincipal(servletRequest);
        authorizationService.requireReservationOwnerOrOperator(reservationId, principal);
        return billingReservationService.checkInPaid(reservationId);
    }

    @PostMapping("/reservations/{reservationId}/check-out")
    public ReservationResponse checkOut(@PathVariable UUID reservationId, HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = requirePrincipal(servletRequest);
        authorizationService.requireReservationOwnerOrOperator(reservationId, principal);
        return billingReservationService.checkOutIdempotent(reservationId);
    }

    @DeleteMapping("/reservations/{reservationId}")
    public CancellationResponse cancel(
            @PathVariable UUID reservationId,
            @RequestBody(required = false) @Valid CancelReservationRequest request,
            HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = requirePrincipal(servletRequest);
        authorizationService.requireReservationOwnerOrOperator(reservationId, principal);
        String reason = request == null ? null : request.reason();
        CancellationResponse response = billingCancellationService.cancel(reservationId, principal.customerAccountId(), reason);
        parkingInvoiceRepository.findByReservation_Id(reservationId)
                .ifPresent(invoice -> outboxDispatcher.dispatchPendingFor(invoice.getId()));
        return response;
    }

    @PutMapping("/reservations/{reservationId}/extend")
    public ExtensionChargeResponse extend(
            @PathVariable UUID reservationId,
            @Valid @RequestBody ExtendReservationRequest request,
            HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = requirePrincipal(servletRequest);
        authorizationService.requireReservationOwnerOrOperator(reservationId, principal);
        ExtensionChargeResponse response = billingExtensionService.extend(reservationId, request.plannedEnd());
        extensionChargeRepository.findByReservation_Id(reservationId)
                .forEach(charge -> outboxDispatcher.dispatchPendingFor(charge.getId()));
        return response;
    }

    @GetMapping("/lots/{lotId}/availability")
    public AvailabilityResponse availability(
            @PathVariable UUID lotId,
            @RequestParam("start") @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime plannedStart,
            @RequestParam("end") @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime plannedEnd,
            @RequestParam(value = "vehicleType", required = false) String vehicleType) {
        return reservationService.getAvailability(lotId, plannedStart, plannedEnd, vehicleType);
    }

    private AuthenticatedPrincipal requirePrincipal(HttpServletRequest servletRequest) {
        Object attribute = servletRequest.getAttribute(JwtAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(attribute instanceof AuthenticatedPrincipal principal)) {
            throw new UnauthorizedException("authentication is required");
        }
        return principal;
    }
}
