package id.xyz.parkease.domain;

import id.xyz.parkease.domain.Reservation.Status;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationTest {

    private static final String SLOT_ID = "A-01";
    private static final String OTHER_SLOT_ID = "B-02";
    private static final String VEHICLE_CAR = "CAR";
    private static final String VEHICLE_MOTO = "MOTO";
    private static final int FLOOR_ONE = 1;
    private static final int FLOOR_TWO = 2;
    private static final String PLANNED_START = "2024-01-15T09:00:00+07:00";
    private static final String PLANNED_END = "2024-01-15T11:00:00+07:00";
    private static final String CHECK_IN_TIME = "2024-01-15T09:30:00+07:00";
    private static final String LATE_CHECK_IN_TIME = "2024-01-15T10:00:00+07:00";
    private static final String CANCEL_REASON = "customer request";

    private ParkingSlot slot() {
        return ParkingSlot.builder().slotId(SLOT_ID).vehicleType(VEHICLE_CAR).floor(FLOOR_ONE).build();
    }

    private Reservation pending() {
        return Reservation.builder()
                .slot(slot())
                .plannedStart(OffsetDateTime.parse(PLANNED_START))
                .plannedEnd(OffsetDateTime.parse(PLANNED_END))
                .build();
    }

    @Test
    void buildWithRequiredFields() {
        Reservation reservation = pending();
        assertNotNull(reservation.getId());
        assertNotNull(reservation.getSlot());
        assertEquals(OffsetDateTime.parse(PLANNED_START), reservation.getPlannedStart());
        assertEquals(OffsetDateTime.parse(PLANNED_END), reservation.getPlannedEnd());
        assertEquals(Status.PENDING, reservation.getStatus());
        assertFalse(reservation.isLateCancellation());
        assertNull(reservation.getActualStart());
        assertNull(reservation.getActualEnd());
        assertNotNull(reservation.getPricingSnapshotJson());
    }

    @Test
    void checkInTransitionsToActive() {
        Reservation active = pending().checkIn(OffsetDateTime.parse(CHECK_IN_TIME));
        assertEquals(Status.ACTIVE, active.getStatus());
        assertEquals(OffsetDateTime.parse(CHECK_IN_TIME), active.getActualStart());
    }

    @Test
    void lateCheckInMarksNoShow() {
        Reservation noShow = pending().markNoShow(OffsetDateTime.parse(LATE_CHECK_IN_TIME));
        assertEquals(Status.NO_SHOW, noShow.getStatus());
        assertEquals(OffsetDateTime.parse(LATE_CHECK_IN_TIME), noShow.getActualStart());
    }

    @Test
    void cancelMarksCancelledWithReason() {
        Reservation cancelled = pending().cancel(CANCEL_REASON, true);
        assertEquals(Status.CANCELLED, cancelled.getStatus());
        assertEquals(CANCEL_REASON, cancelled.getCancellationReason());
        assertTrue(cancelled.isLateCancellation());
    }

    @Test
    void completeTransitionsToCompleted() {
        OffsetDateTime end = OffsetDateTime.parse(PLANNED_END);
        Reservation completed = pending().checkIn(OffsetDateTime.parse(CHECK_IN_TIME)).complete(end);
        assertEquals(Status.COMPLETED, completed.getStatus());
        assertEquals(end, completed.getActualEnd());
    }

    @Test
    void checkInRejectsNullStart() {
        assertThrows(NullPointerException.class, () -> pending().checkIn(null));
    }

    @Test
    void completeRejectsNullEnd() {
        assertThrows(NullPointerException.class, () -> pending().complete(null));
    }

    @Test
    void applyDefaultsFillsNulls() {
        Reservation reservation = new Reservation(null, slot(), null, null, null, null, null, null, null, false, null, 0, null, null, null);
        reservation.applyDefaults();
        assertNotNull(reservation.getId());
        assertEquals(Status.PENDING, reservation.getStatus());
        assertNotNull(reservation.getPricingSnapshotJson());
        assertNotNull(reservation.getCreatedAt());
        assertNotNull(reservation.getUpdatedAt());
    }

    @Test
    void applyDefaultsKeepsExistingValues() {
        Reservation reservation = pending();
        UUID id = reservation.getId();
        reservation.applyDefaults();
        assertEquals(id, reservation.getId());
        assertEquals(Status.PENDING, reservation.getStatus());
    }

    @Test
    void generatedIdsAreUnique() {
        ParkingSlot otherSlot = ParkingSlot.builder().slotId(OTHER_SLOT_ID).vehicleType(VEHICLE_MOTO).floor(FLOOR_TWO).build();
        UUID first = Reservation.builder().slot(slot()).plannedStart(OffsetDateTime.now()).plannedEnd(OffsetDateTime.now()).build().getId();
        UUID second = Reservation.builder().slot(otherSlot).plannedStart(OffsetDateTime.now()).plannedEnd(OffsetDateTime.now()).build().getId();
        assertNotEquals(first, second);
    }
}
