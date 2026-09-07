package id.xyz.parkease.domain;

import id.xyz.parkease.domain.PricingPromotion.DiscountType;
import id.xyz.parkease.domain.PricingPromotion.PromoStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PricingPromotionTest {

    private static final String PROMO_CODE = "PROMO10";
    private static final String OTHER_CODE = "PROMO20";
    private static final String START = "2024-01-01T00:00:00+07:00";
    private static final String END = "2024-12-31T23:59:59+07:00";
    private static final BigDecimal TEN = BigDecimal.valueOf(10.0);
    private static final BigDecimal DISCOUNT = BigDecimal.valueOf(25.50);
    private static final int USAGE_LIMIT = 50;

    private PricingPromotion promo() {
        return PricingPromotion.builder()
                .code(PROMO_CODE)
                .effectiveFrom(OffsetDateTime.parse(START))
                .effectiveTo(OffsetDateTime.parse(END))
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(TEN)
                .build();
    }

    @Test
    void buildWithRequiredFields() {
        PricingPromotion saved = promo();
        assertEquals(PROMO_CODE, saved.getCode());
        assertEquals(DiscountType.PERCENTAGE, saved.getDiscountType());
        assertEquals(TEN, saved.getDiscountValue());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void buildWithUsageLimit() {
        PricingPromotion saved = PricingPromotion.builder()
                .code(PROMO_CODE)
                .effectiveFrom(OffsetDateTime.parse(START))
                .effectiveTo(OffsetDateTime.parse(END))
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(TEN)
                .usageLimit(USAGE_LIMIT)
                .build();
        assertEquals(Integer.valueOf(USAGE_LIMIT), saved.getUsageLimit());
        assertEquals(0, saved.getUsageCount());
        assertEquals(PromoStatus.ACTIVE, saved.getStatus());
    }

    @Test
    void consumeIncrementsUsage() {
        assertEquals(1, promo().consume().getUsageCount());
    }

    @Test
    void applyDefaultsKeepsExistingValues() {
        PricingPromotion promotion = promo();
        String code = promotion.getCode();
        promotion.applyDefaults();
        assertEquals(code, promotion.getCode());
        assertEquals(PromoStatus.ACTIVE, promotion.getStatus());
    }

    @Test
    void applyDefaultsFillsNulls() {
        PricingPromotion promotion = new PricingPromotion(null, null, null, null, OffsetDateTime.parse(START), OffsetDateTime.parse(END), DiscountType.PERCENTAGE, TEN, null, 0, null, null, null);
        promotion.applyDefaults();
        assertNotNull(promotion.getCode());
        assertEquals(PromoStatus.ACTIVE, promotion.getStatus());
        assertNotNull(promotion.getCreatedAt());
        assertNotNull(promotion.getUpdatedAt());
    }

    @Test
    void generatedCodesAreDistinct() {
        PricingPromotion first = PricingPromotion.builder()
                .code(PROMO_CODE)
                .effectiveFrom(OffsetDateTime.now())
                .effectiveTo(OffsetDateTime.now())
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.ONE)
                .build();
        PricingPromotion second = PricingPromotion.builder()
                .code(OTHER_CODE)
                .effectiveFrom(OffsetDateTime.now())
                .effectiveTo(OffsetDateTime.now())
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.ONE)
                .build();
        assertNotEquals(first.getCode(), second.getCode());
    }

    @Test
    void discountValueIsStored() {
        PricingPromotion saved = PricingPromotion.builder()
                .code(PROMO_CODE)
                .effectiveFrom(OffsetDateTime.now())
                .effectiveTo(OffsetDateTime.now())
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(DISCOUNT)
                .build();
        assertEquals(DISCOUNT, saved.getDiscountValue());
    }
}
