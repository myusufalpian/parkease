package id.xyz.parkease.service;

import id.xyz.parkease.domain.DemandPricingRule;
import id.xyz.parkease.domain.RateCard;
import id.xyz.parkease.repository.DemandPricingRuleRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import id.xyz.parkease.repository.ReservationRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DemandPricingService {
    private final DemandPricingRuleRepository ruleRepository;
    private final ParkingSlotRepository slotRepository;
    private final ReservationRepository reservationRepository;

    public DemandPricingService(DemandPricingRuleRepository ruleRepository, ParkingSlotRepository slotRepository, ReservationRepository reservationRepository) {
        this.ruleRepository = Objects.requireNonNull(ruleRepository);
        this.slotRepository = Objects.requireNonNull(slotRepository);
        this.reservationRepository = Objects.requireNonNull(reservationRepository);
    }

    public DemandAdjustment resolve(UUID lotId, String vehicleType, OffsetDateTime start, OffsetDateTime end, UUID excludedReservationId, RateCard rateCard) {
        long capacity = slotRepository.countByLot_IdAndVehicleType(lotId, vehicleType);
        if (capacity == 0) return new DemandAdjustment(rateCard, "0.0000");
        long occupied = reservationRepository.findOverlapping(start, end, java.util.List.of(id.xyz.parkease.domain.Reservation.Status.PENDING, id.xyz.parkease.domain.Reservation.Status.ACTIVE)).stream()
                .filter(reservation -> !reservation.getId().equals(excludedReservationId))
                .filter(reservation -> reservation.getSlot().getLot().getId().equals(lotId))
                .filter(reservation -> reservation.getSlot().getVehicleType().equals(vehicleType)).count();
        BigDecimal occupancy = BigDecimal.valueOf(occupied).divide(BigDecimal.valueOf(capacity), 4, java.math.RoundingMode.HALF_UP);
        DemandPricingRule rule = ruleRepository.findByLot_IdAndStatusOrderByOccupancyThresholdDesc(lotId, DemandPricingRule.RuleStatus.ACTIVE).stream()
                .filter(candidate -> candidate.getEffectiveFrom().compareTo(start) <= 0 && candidate.getEffectiveTo().compareTo(start) >= 0)
                .filter(candidate -> candidate.getVehicleType() == null || candidate.getVehicleType().equals(vehicleType))
                .filter(candidate -> occupancy.compareTo(candidate.getOccupancyThreshold()) >= 0).findFirst().orElse(null);
        if (rule == null) return new DemandAdjustment(rateCard, occupancy.toPlainString());
        RateCard adjusted = rateCard.toBuilder().hourlyRate(rateCard.getHourlyRate().multiply(rule.getMultiplier())).build();
        return new DemandAdjustment(adjusted, occupancy.toPlainString());
    }

    public record DemandAdjustment(RateCard rateCard, String metric) { }
}
