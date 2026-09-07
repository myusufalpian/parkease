package id.xyz.parkease.service;

import id.xyz.parkease.domain.RateCard;
import id.xyz.parkease.domain.RateCard.RateCardStatus;
import id.xyz.parkease.exception.BusinessValidationException;
import id.xyz.parkease.repository.RateCardRepository;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RateCardResolver {

    private final RateCardRepository rateCardRepository;

    public RateCardResolver(RateCardRepository rateCardRepository) {
        this.rateCardRepository = Objects.requireNonNull(rateCardRepository);
    }

    public RateCard resolve(UUID lotId, String vehicleType, OffsetDateTime bookingTime) {
        return rateCardRepository
                .findByLot_IdAndVehicleTypeAndStatusOrderByVersionDesc(lotId, vehicleType, RateCardStatus.ACTIVE)
                .stream()
                .filter(rateCard -> rateCard.isEffectiveAt(bookingTime))
                .findFirst()
                .orElseThrow(() -> new BusinessValidationException(
                        "no active rate card is configured for the requested lot and vehicle type"));
    }
}
