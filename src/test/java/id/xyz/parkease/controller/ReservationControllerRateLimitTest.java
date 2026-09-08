package id.xyz.parkease.controller;

import id.xyz.parkease.dto.AvailabilityResponse;
import id.xyz.parkease.repository.ExtensionChargeRepository;
import id.xyz.parkease.repository.ParkingInvoiceRepository;
import id.xyz.parkease.security.PublicApiRateLimiter;
import id.xyz.parkease.service.AuthorizationService;
import id.xyz.parkease.service.BillingCancellationService;
import id.xyz.parkease.service.BillingExtensionService;
import id.xyz.parkease.service.BillingReservationService;
import id.xyz.parkease.service.OutboxDispatcher;
import id.xyz.parkease.service.ReservationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationControllerRateLimitTest {

    @Test
    void availabilityChecksRateLimitAndDelegates() {
        ReservationService reservationService = mock(ReservationService.class);
        BillingReservationService billingReservationService = mock(BillingReservationService.class);
        BillingCancellationService billingCancellationService = mock(BillingCancellationService.class);
        BillingExtensionService billingExtensionService = mock(BillingExtensionService.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        OutboxDispatcher outboxDispatcher = mock(OutboxDispatcher.class);
        ParkingInvoiceRepository parkingInvoiceRepository = mock(ParkingInvoiceRepository.class);
        ExtensionChargeRepository extensionChargeRepository = mock(ExtensionChargeRepository.class);
        PublicApiRateLimiter rateLimiter = mock(PublicApiRateLimiter.class);

        ReservationController controller = new ReservationController(
                reservationService, billingReservationService, billingCancellationService,
                billingExtensionService, authorizationService, outboxDispatcher,
                parkingInvoiceRepository, extensionChargeRepository, rateLimiter);

        UUID lotId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.parse("2024-01-15T09:00:00+07:00");
        OffsetDateTime end = OffsetDateTime.parse("2024-01-15T11:00:00+07:00");
        String vehicleType = "CAR";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        AvailabilityResponse expected = new AvailabilityResponse(lotId, start, end, List.of());
        when(reservationService.getAvailability(lotId, start, end, vehicleType)).thenReturn(expected);

        AvailabilityResponse actual = controller.availability(lotId, start, end, vehicleType, request);

        verify(rateLimiter).check("lots:availability", "127.0.0.1");
        verify(reservationService).getAvailability(lotId, start, end, vehicleType);
        assertEquals(expected, actual);
    }
}
