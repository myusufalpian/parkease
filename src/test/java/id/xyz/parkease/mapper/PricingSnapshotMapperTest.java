package id.xyz.parkease.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.xyz.parkease.dto.PricingSnapshot;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PricingSnapshotMapperTest {

    private static final int RATE_VERSION = 1;
    private static final String DEMAND = "normal";
    private static final String PROMO = "PROMO10";
    private static final int GRACE = 30;

    private final PricingSnapshotMapper mapper = new PricingSnapshotMapper(new ObjectMapper());

    private static final class BrokenJsonException extends JsonProcessingException {
        BrokenJsonException() {
            super("broken");
        }
    }

    private static final class FailingMapper extends ObjectMapper {
        @Override
        public String writeValueAsString(Object value) throws JsonProcessingException {
            throw new BrokenJsonException();
        }
    }

    private PricingSnapshot snapshot() {
        return new PricingSnapshot(
                RATE_VERSION, DEMAND, PROMO, BigDecimal.valueOf(10000), BigDecimal.valueOf(80000), GRACE, BigDecimal.valueOf(15000));
    }

    @Test
    void roundTripPreservesSnapshot() {
        assertEquals(snapshot(), mapper.fromJson(mapper.toJson(snapshot())));
    }

    @Test
    void toJsonRejectsNull() {
        assertThrows(NullPointerException.class, () -> mapper.toJson(null));
    }

    @Test
    void fromJsonRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> mapper.fromJson("  "));
    }

    @Test
    void fromJsonRejectsInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> mapper.fromJson("{broken"));
    }

    @Test
    void toJsonWrapsSerializationFailure() {
        PricingSnapshotMapper failing = new PricingSnapshotMapper(new FailingMapper());
        assertThrows(IllegalArgumentException.class, () -> failing.toJson(snapshot()));
    }
}
