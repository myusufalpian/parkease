package id.xyz.parkease.controller;

import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.dto.LotResponse;
import id.xyz.parkease.dto.SlotResponse;
import id.xyz.parkease.exception.ResourceNotFoundException;
import id.xyz.parkease.mapper.LotMapper;
import id.xyz.parkease.mapper.SlotMapper;
import id.xyz.parkease.repository.ParkingLotRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import id.xyz.parkease.security.PublicApiRateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({LotController.class, LotMapper.class, SlotMapper.class, PublicApiRateLimiter.class, LotControllerTest.TestBeans.class})
class LotControllerTest {

    @Autowired
    private LotController lotController;

    @Autowired
    private ParkingLotRepository lotRepository;

    @Autowired
    private ParkingSlotRepository slotRepository;

    @BeforeEach
    void clean() {
        slotRepository.deleteAll();
        lotRepository.deleteAll();
    }

    @Test
    void listsLotsDeterministically() {
        ParkingLot first = lotRepository.saveAndFlush(ParkingLot.builder().name("B").timezone("Asia/Jakarta").build());
        ParkingLot second = lotRepository.saveAndFlush(ParkingLot.builder().name("A").timezone("Asia/Jakarta").build());
        List<LotResponse> lots = lotController.listLots(request());
        assertEquals(2, lots.size());
    }

    @Test
    void getsLotById() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        LotResponse response = lotController.getLot(lot.getId(), request());
        assertEquals(lot.getId(), response.id());
        assertEquals("L", response.name());
    }

    @Test
    void throwsForUnknownLot() {
        assertThrows(ResourceNotFoundException.class, () -> lotController.getLot(UUID.randomUUID(), request()));
    }

    @Test
    void listsSlotsOrderedByFloorAndSlotId() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("B-01").vehicleType("CAR").floor(2).build());
        ParkingSlot a02 = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-02").vehicleType("CAR").floor(1).build());
        ParkingSlot a01 = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        List<SlotResponse> slots = lotController.listSlots(lot.getId(), request());
        assertEquals("A-01", slots.get(0).slotId());
        assertEquals("A-02", slots.get(1).slotId());
        assertEquals("B-01", slots.get(2).slotId());
    }

    @Test
    void throwsForSlotsOfUnknownLot() {
        assertThrows(ResourceNotFoundException.class, () -> lotController.listSlots(UUID.randomUUID(), request()));
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2024-01-15T01:00:00Z"), ZoneOffset.UTC);
        }
    }
}
