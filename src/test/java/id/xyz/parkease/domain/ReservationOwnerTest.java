package id.xyz.parkease.domain;

import id.xyz.parkease.domain.Reservation.Status;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReservationOwnerTest {

    private static final String SLOT_ID = "A-01";
    private static final String VEHICLE_CAR = "CAR";
    private static final int FLOOR_ONE = 1;

    private ParkingSlot slot() {
        return ParkingSlot.builder().slotId(SLOT_ID).vehicleType(VEHICLE_CAR).floor(FLOOR_ONE).build();
    }

    private Reservation reservation() {
        return Reservation.builder()
                .slot(slot())
                .plannedStart(OffsetDateTime.parse("2024-01-15T09:00:00+07:00"))
                .plannedEnd(OffsetDateTime.parse("2024-01-15T11:00:00+07:00"))
                .status(Status.PENDING)
                .build();
    }

    private CustomerAccount account() {
        return CustomerAccount.builder().username("driver1").passwordHash("hash").build();
    }

    @Test
    void buildLinksReservationAndCustomerAccount() {
        Reservation reservation = reservation();
        CustomerAccount account = account();

        ReservationOwner owner = ReservationOwner.builder()
                .reservationId(reservation.getId())
                .reservation(reservation)
                .customerAccount(account)
                .build();

        assertEquals(reservation.getId(), owner.getReservationId());
        assertEquals(reservation, owner.getReservation());
        assertEquals(account, owner.getCustomerAccount());
        assertNotNull(owner.getCreatedAt());
    }

    @Test
    void applyDefaultsFillsCreatedAtWhenNull() {
        Reservation reservation = reservation();
        ReservationOwner owner = new ReservationOwner(reservation.getId(), reservation, account(), null);
        owner.applyDefaults();
        assertNotNull(owner.getCreatedAt());
    }

    @Test
    void applyDefaultsKeepsExistingCreatedAt() {
        ReservationOwner owner = ReservationOwner.builder()
                .reservationId(reservation().getId())
                .reservation(reservation())
                .customerAccount(account())
                .build();
        OffsetDateTime createdAt = owner.getCreatedAt();
        owner.applyDefaults();
        assertEquals(createdAt, owner.getCreatedAt());
    }
}
