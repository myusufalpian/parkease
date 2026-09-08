package id.xyz.parkease.service;

import id.xyz.parkease.config.BookingProperties;
import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.dto.ReservationRequest;
import id.xyz.parkease.exception.ConflictException;
import id.xyz.parkease.mapper.ReservationMapper;
import id.xyz.parkease.repository.ParkingLotRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import id.xyz.parkease.repository.ReservationRepository;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReservationServiceExceptionTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2024-01-15T09:00:00+07:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2024-01-15T11:00:00+07:00");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2024-01-15T01:00:00Z");

    @Test
    void createMapsOverlapViolationToConflict() {
        ParkingLot lot = ParkingLot.builder().timezone("Asia/Jakarta").build();
        lot = lot.toBuilder().id(UUID.randomUUID()).build();
        ParkingSlot slot = ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build();

        ParkingLotRepository lotRepo = fakeLotRepository(lot);
        ParkingSlotRepository slotRepo = fakeSlotRepository(slot);
        ReservationRepository reservationRepo = fakeReservationRepository(new DataIntegrityViolationException("conflict", new SQLException("exclusion", "23P01")));
        ReservationMapper mapper = new ReservationMapper();
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        BookingProperties props = new BookingProperties(30, 90);
        Clock clock = Clock.fixed(Instant.parse("2024-01-15T01:00:00Z"), ZoneOffset.UTC);
        BookingWindowValidator validator = new BookingWindowValidator(props, clock);
        InventoryConflictService conflictService = mock(InventoryConflictService.class);
        when(conflictService.requireNoConflictForReservation(slot.getId(), START, END, null)).thenReturn(slot);

        ReservationService service = new ReservationService(lotRepo, slotRepo, reservationRepo, mapper, publisher, validator, conflictService, clock);
        ReservationRequest request = new ReservationRequest(lot.getId(), "CAR", "PLATE", START, END);
        assertThrows(ConflictException.class, () -> service.createReservation(request, NOW));
    }

    private ParkingLotRepository fakeLotRepository(ParkingLot lot) {
        return (ParkingLotRepository) Proxy.newProxyInstance(
                ParkingLotRepository.class.getClassLoader(),
                new Class[]{ParkingLotRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) return Optional.of(lot);
                    Class<?> ret = method.getReturnType();
                    if (ret.equals(Optional.class)) return Optional.empty();
                    if (ret.equals(List.class)) return List.of();
                    return null;
                });
    }

    private ParkingSlotRepository fakeSlotRepository(ParkingSlot slot) {
        return (ParkingSlotRepository) Proxy.newProxyInstance(
                ParkingSlotRepository.class.getClassLoader(),
                new Class[]{ParkingSlotRepository.class},
                (proxy, method, args) -> {
                    if ("findAvailableSlots".equals(method.getName())) return List.of(slot);
                    if ("findByLot_IdOrderByFloorAscSlotIdAsc".equals(method.getName())) return List.of(slot);
                    if ("findByIdForUpdate".equals(method.getName())) return Optional.of(slot);
                    if ("save".equals(method.getName())) return slot.toBuilder().status(ParkingSlot.SlotStatus.RESERVED).build();
                    if ("findById".equals(method.getName())) return Optional.of(slot);
                    Class<?> ret = method.getReturnType();
                    if (ret.equals(Optional.class)) return Optional.empty();
                    if (ret.equals(List.class)) return List.of();
                    return null;
                });
    }

    @Test
    void extendMapsOverlapViolationToConflict() {
        ParkingLot lot = ParkingLot.builder().timezone("Asia/Jakarta").build();
        lot = lot.toBuilder().id(UUID.randomUUID()).build();
        ParkingSlot slot = ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build();
        UUID reservationId = UUID.randomUUID();

        ParkingLotRepository lotRepo = fakeLotRepository(lot);
        ParkingSlotRepository slotRepo = fakeSlotRepository(slot);
        ReservationRepository reservationRepo = (ReservationRepository) Proxy.newProxyInstance(
                ReservationRepository.class.getClassLoader(),
                new Class[]{ReservationRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        UUID argId = (UUID) args[0];
                        if (argId.equals(reservationId)) {
                            return Optional.of(id.xyz.parkease.domain.Reservation.builder().id(reservationId).slot(slot).plannedStart(START).plannedEnd(END).status(id.xyz.parkease.domain.Reservation.Status.PENDING).build());
                        }
                        return Optional.empty();
                    }
                    if ("saveAndFlush".equals(method.getName())) throw new DataIntegrityViolationException("conflict", new SQLException("exclusion", "23P01"));
                    if ("save".equals(method.getName())) throw new DataIntegrityViolationException("conflict", new SQLException("exclusion", "23P01"));
                    Class<?> ret = method.getReturnType();
                    if (ret.equals(Optional.class)) return Optional.empty();
                    if (ret.equals(List.class)) return List.of();
                    return null;
                });

        ReservationMapper mapper = new ReservationMapper();
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        BookingProperties props = new BookingProperties(30, 90);
        Clock clock = Clock.fixed(Instant.parse("2024-01-15T01:00:00Z"), ZoneOffset.UTC);
        BookingWindowValidator validator = new BookingWindowValidator(props, clock);
        InventoryConflictService conflictService = mock(InventoryConflictService.class);
        when(conflictService.lockSlot(slot.getId())).thenReturn(slot);
        when(conflictService.hasActiveBlockOverlap(slot.getId(), START, END.plusHours(1))).thenReturn(false);

        ReservationService service = new ReservationService(lotRepo, slotRepo, reservationRepo, mapper, publisher, validator, conflictService, clock);
        assertThrows(ConflictException.class, () -> service.extend(reservationId, END.plusHours(1), NOW));
    }

    @SuppressWarnings("unchecked")
    private ReservationRepository fakeReservationRepository(RuntimeException toThrow) {
        return (ReservationRepository) Proxy.newProxyInstance(
                ReservationRepository.class.getClassLoader(),
                new Class[]{ReservationRepository.class},
                (proxy, method, args) -> {
                    if ("saveAndFlush".equals(method.getName())) throw toThrow;
                    if ("save".equals(method.getName())) throw toThrow;
                    Class<?> ret = method.getReturnType();
                    if (ret.equals(Optional.class)) return Optional.empty();
                    if (ret.equals(List.class)) return List.of();
                    if (ret.equals(void.class)) return null;
                    if (ret.equals(boolean.class)) return false;
                    if (ret.equals(long.class)) return 0L;
                    return null;
                });
    }
}
