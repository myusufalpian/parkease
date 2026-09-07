package id.xyz.parkease.domain;

import id.xyz.parkease.dto.PricingSnapshot;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingSnapshotTest {

    private static final int RATE_VERSION = 1;
    private static final String DEMAND = "normal";
    private static final String PROMO = "PROMO10";
    private static final int GRACE = 30;

    @Test
    void recordStoresPricingDecision() {
        BigDecimal rate = BigDecimal.valueOf(10000);
        BigDecimal cap = BigDecimal.valueOf(80000);
        BigDecimal surcharge = BigDecimal.valueOf(15000);
        PricingSnapshot snapshot = new PricingSnapshot(RATE_VERSION, DEMAND, PROMO, rate, cap, GRACE, surcharge);
        assertEquals(RATE_VERSION, snapshot.rateCardVersion());
        assertEquals(DEMAND, snapshot.demandMetric());
        assertEquals(PROMO, snapshot.promoCode());
        assertEquals(rate, snapshot.hourlyRate());
        assertEquals(cap, snapshot.dailyCap());
        assertEquals(GRACE, snapshot.graceMinutes());
        assertEquals(surcharge, snapshot.overnightSurcharge());
    }
}
