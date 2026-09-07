package id.xyz.parkease.service;

import id.xyz.parkease.domain.CustomerAccount.Role;
import id.xyz.parkease.domain.ReservationOwner;
import id.xyz.parkease.exception.ForbiddenException;
import id.xyz.parkease.exception.ResourceNotFoundException;
import id.xyz.parkease.repository.ReservationOwnerRepository;
import id.xyz.parkease.security.AuthenticatedPrincipal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    private final ReservationOwnerRepository reservationOwnerRepository;

    public AuthorizationService(ReservationOwnerRepository reservationOwnerRepository) {
        this.reservationOwnerRepository = Objects.requireNonNull(reservationOwnerRepository);
    }

    public void requireReservationOwnerOrOperator(UUID reservationId, AuthenticatedPrincipal principal) {
        if (principal.role() == Role.OPERATOR || principal.role() == Role.ADMIN) {
            return;
        }
        ReservationOwner owner = reservationOwnerRepository.findByReservation_Id(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("reservation was not found"));
        if (!owner.getCustomerAccount().getId().equals(principal.customerAccountId())) {
            throw new ForbiddenException("principal does not own this reservation");
        }
    }
}
