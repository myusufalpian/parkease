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
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class LotController {

    private final ParkingLotRepository parkingLotRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final LotMapper lotMapper;
    private final SlotMapper slotMapper;
    private final PublicApiRateLimiter rateLimiter;

    public LotController(
            ParkingLotRepository parkingLotRepository,
            ParkingSlotRepository parkingSlotRepository,
            LotMapper lotMapper,
            SlotMapper slotMapper,
            PublicApiRateLimiter rateLimiter) {
        this.parkingLotRepository = Objects.requireNonNull(parkingLotRepository);
        this.parkingSlotRepository = Objects.requireNonNull(parkingSlotRepository);
        this.lotMapper = Objects.requireNonNull(lotMapper);
        this.slotMapper = Objects.requireNonNull(slotMapper);
        this.rateLimiter = Objects.requireNonNull(rateLimiter);
    }

    @GetMapping("/lots")
    public List<LotResponse> listLots(HttpServletRequest request) {
        rateLimiter.check("lots:list", clientIp(request));
        return parkingLotRepository.findAll().stream()
                .sorted(Comparator.comparing(ParkingLot::getId))
                .map(lotMapper::toResponse)
                .toList();
    }

    @GetMapping("/lots/{lotId}")
    public LotResponse getLot(@PathVariable UUID lotId, HttpServletRequest request) {
        rateLimiter.check("lots:detail", clientIp(request));
        return parkingLotRepository.findById(lotId)
                .map(lotMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("parking lot was not found"));
    }

    @GetMapping("/lots/{lotId}/slots")
    public List<SlotResponse> listSlots(@PathVariable UUID lotId, HttpServletRequest request) {
        rateLimiter.check("lots:slots", clientIp(request));
        parkingLotRepository.findById(lotId)
                .orElseThrow(() -> new ResourceNotFoundException("parking lot was not found"));
        List<ParkingSlot> slots = parkingSlotRepository.findByLot_IdOrderByFloorAscSlotIdAsc(lotId);
        return slots.stream()
                .map(slotMapper::toResponse)
                .toList();
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
