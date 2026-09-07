package id.xyz.parkease.controller;

import id.xyz.parkease.dto.InvoiceResponse;
import id.xyz.parkease.exception.UnauthorizedException;
import id.xyz.parkease.security.AuthenticatedPrincipal;
import id.xyz.parkease.security.JwtAuthenticationFilter;
import id.xyz.parkease.service.AuthorizationService;
import id.xyz.parkease.service.BillingReservationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BillingController {

    private final BillingReservationService billingReservationService;
    private final AuthorizationService authorizationService;

    public BillingController(BillingReservationService billingReservationService, AuthorizationService authorizationService) {
        this.billingReservationService = billingReservationService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/invoices/{reservationId}")
    public InvoiceResponse getInvoice(@PathVariable UUID reservationId, HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = requirePrincipal(servletRequest);
        authorizationService.requireReservationOwnerOrOperator(reservationId, principal);
        return billingReservationService.getInvoice(reservationId);
    }

    private AuthenticatedPrincipal requirePrincipal(HttpServletRequest servletRequest) {
        Object attribute = servletRequest.getAttribute(JwtAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(attribute instanceof AuthenticatedPrincipal principal)) {
            throw new UnauthorizedException("authentication is required");
        }
        return principal;
    }
}
