package id.xyz.parkease.service;

import id.xyz.parkease.domain.PricingPromotion;
import id.xyz.parkease.domain.PromotionHold;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.exception.BusinessValidationException;
import id.xyz.parkease.repository.PricingPromotionRepository;
import id.xyz.parkease.repository.PromotionHoldRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PromotionService {
    private final PricingPromotionRepository promotionRepository;
    private final PromotionHoldRepository holdRepository;
    private final Clock clock;

    public PromotionService(PricingPromotionRepository promotionRepository, PromotionHoldRepository holdRepository, Clock clock) {
        this.promotionRepository = Objects.requireNonNull(promotionRepository);
        this.holdRepository = Objects.requireNonNull(holdRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public PricingPromotion hold(String code, Reservation reservation, String vehicleType, UUID lotId, String customerType) {
        if (code == null || code.isBlank()) return null;
        OffsetDateTime now = OffsetDateTime.now(clock);
        PricingPromotion promotion = promotionRepository.findEligible(code, now, PricingPromotion.PromoStatus.ACTIVE, lotId, vehicleType, customerType)
                .orElseThrow(() -> new BusinessValidationException("promo code is not eligible"));
        if (promotionRepository.claim(code, PricingPromotion.PromoStatus.ACTIVE, now) != 1) {
            throw new BusinessValidationException("promo code usage limit has been reached");
        }
        holdRepository.save(PromotionHold.builder().reservation(reservation).promotion(promotion).build());
        return promotion;
    }

    public void consume(UUID reservationId) {
        transition(reservationId, PromotionHold.HoldStatus.HELD, PromotionHold.HoldStatus.CONSUMED, false);
    }

    public void release(UUID reservationId) {
        transition(reservationId, PromotionHold.HoldStatus.HELD, PromotionHold.HoldStatus.RELEASED, true);
    }

    public PricingPromotion findHeld(UUID reservationId) {
        return holdRepository.findByReservation_Id(reservationId)
                .filter(hold -> hold.getStatus() == PromotionHold.HoldStatus.HELD)
                .map(PromotionHold::getPromotion)
                .orElse(null);
    }

    private void transition(UUID reservationId, PromotionHold.HoldStatus expected, PromotionHold.HoldStatus target, boolean decrement) {
        holdRepository.findByReservation_Id(reservationId).filter(hold -> hold.getStatus() == expected).ifPresent(hold -> {
            int transitioned = holdRepository.transition(reservationId, expected, target);
            if (decrement && transitioned == 1) {
                promotionRepository.release(hold.getPromotion().getCode(), OffsetDateTime.now(clock));
            }
        });
    }
}
