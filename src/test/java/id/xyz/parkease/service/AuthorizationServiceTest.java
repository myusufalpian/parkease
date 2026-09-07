package id.xyz.parkease.service;

import id.xyz.parkease.domain.CustomerAccount;
import id.xyz.parkease.domain.CustomerAccount.Role;
import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.domain.Reservation.Status;
import id.xyz.parkease.domain.ReservationOwner;
import id.xyz.parkease.exception.ForbiddenException;
import id.xyz.parkease.exception.ResourceNotFoundException;
import id.xyz.parkease.repository.CustomerAccountRepository;
import id.xyz.parkease.repository.ParkingLotRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import id.xyz.parkease.repository.ReservationOwnerRepository;
import id.xyz.parkease.repository.ReservationRepository;
import id.xyz.parkease.security.AuthenticatedPrincipal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AuthorizationService.class)
class AuthorizationServiceTest {

    private static final String SLOT_ID = "A-01";
    private static final String VEHICLE_CAR = "CAR";
    private static final int FLOOR_ONE = 1;
    private static final String LOT_TIMEZONE = "Asia/Jakarta";

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private CustomerAccountRepository customerAccountRepository;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private ParkingSlotRepository parkingSlotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationOwnerRepository reservationOwnerRepository;

    @BeforeEach
    void cleanDatabase() {
        reservationOwnerRepository.deleteAll();
        reservationRepository.deleteAll();
        parkingSlotRepository.deleteAll();
        parkingLotRepository.deleteAll();
        customerAccountRepository.deleteAll();
    }

    private CustomerAccount account(String username, Role role) {
        return customerAccountRepository.saveAndFlush(
                CustomerAccount.builder().username(username).passwordHash("hash").role(role).build());
    }

    private Reservation reservation() {
        ParkingLot lot = parkingLotRepository.saveAndFlush(
                ParkingLot.builder().name("Test Lot").timezone(LOT_TIMEZONE).build());
        ParkingSlot slot = parkingSlotRepository.saveAndFlush(
                ParkingSlot.builder().lot(lot).slotId(SLOT_ID).vehicleType(VEHICLE_CAR).floor(FLOOR_ONE).build());
        return reservationRepository.saveAndFlush(Reservation.builder()
                .slot(slot)
                .plannedStart(OffsetDateTime.parse("2024-01-15T09:00:00+07:00"))
                .plannedEnd(OffsetDateTime.parse("2024-01-15T11:00:00+07:00"))
                .status(Status.PENDING)
                .build());
    }

    private void assignOwner(Reservation reservation, CustomerAccount owner) {
        reservationOwnerRepository.saveAndFlush(ReservationOwner.builder()
                .reservation(reservation)
                .customerAccount(owner)
                .build());
    }

    @Test
    void ownerIsAllowed() {
        Reservation reservation = reservation();
        CustomerAccount owner = account("owner1", Role.CUSTOMER);
        assignOwner(reservation, owner);

        assertDoesNotThrow(() -> authorizationService.requireReservationOwnerOrOperator(
                reservation.getId(), new AuthenticatedPrincipal(owner.getId(), Role.CUSTOMER)));
    }

    @Test
    void nonOwnerCustomerIsForbidden() {
        Reservation reservation = reservation();
        CustomerAccount owner = account("owner1", Role.CUSTOMER);
        CustomerAccount other = account("other1", Role.CUSTOMER);
        assignOwner(reservation, owner);

        assertThrows(
                ForbiddenException.class,
                () -> authorizationService.requireReservationOwnerOrOperator(
                        reservation.getId(), new AuthenticatedPrincipal(other.getId(), Role.CUSTOMER)));
    }

    @Test
    void operatorBypassesOwnershipCheck() {
        Reservation reservation = reservation();
        CustomerAccount owner = account("owner1", Role.CUSTOMER);
        CustomerAccount operator = account("operator1", Role.OPERATOR);
        assignOwner(reservation, owner);

        assertDoesNotThrow(() -> authorizationService.requireReservationOwnerOrOperator(
                reservation.getId(), new AuthenticatedPrincipal(operator.getId(), Role.OPERATOR)));
    }

    @Test
    void adminBypassesOwnershipCheck() {
        Reservation reservation = reservation();
        CustomerAccount owner = account("owner1", Role.CUSTOMER);
        CustomerAccount admin = account("admin1", Role.ADMIN);
        assignOwner(reservation, owner);

        assertDoesNotThrow(() -> authorizationService.requireReservationOwnerOrOperator(
                reservation.getId(), new AuthenticatedPrincipal(admin.getId(), Role.ADMIN)));
    }

    @Test
    void unknownReservationIsNotFound() {
        CustomerAccount customer = account("customer1", Role.CUSTOMER);

        assertThrows(
                ResourceNotFoundException.class,
                () -> authorizationService.requireReservationOwnerOrOperator(
                        UUID.randomUUID(), new AuthenticatedPrincipal(customer.getId(), Role.CUSTOMER)));
    }
}
