package id.xyz.parkease.domain;

import java.lang.reflect.Field;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JsonbMappingContractTest {

    @Test
    void parkingLotOperatingHoursUsesJsonJdbcType() throws Exception {
        assertJsonJdbcType(ParkingLot.class, "operatingHours");
    }

    @Test
    void reservationPricingSnapshotUsesJsonJdbcType() throws Exception {
        assertJsonJdbcType(Reservation.class, "pricingSnapshotJson");
    }

    private void assertJsonJdbcType(Class<?> entityType, String fieldName) throws Exception {
        Field field = entityType.getDeclaredField(fieldName);
        JdbcTypeCode jdbcTypeCode = field.getAnnotation(JdbcTypeCode.class);

        assertNotNull(jdbcTypeCode);
        assertEquals(SqlTypes.JSON, jdbcTypeCode.value());
    }
}
