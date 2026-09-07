package id.xyz.parkease.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.xyz.parkease.dto.PaymentReferenceSnapshot;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PaymentReferenceSnapshotMapper {

    private static final String BLANK_MESSAGE = "payment reference snapshot json must not be blank";

    private final ObjectMapper objectMapper;

    public PaymentReferenceSnapshotMapper() {
        this(new ObjectMapper());
    }

    public PaymentReferenceSnapshotMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public String toJson(PaymentReferenceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to serialize payment reference snapshot", e);
        }
    }

    public PaymentReferenceSnapshot fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            throw new IllegalArgumentException(BLANK_MESSAGE);
        }
        try {
            return objectMapper.readValue(json, PaymentReferenceSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to deserialize payment reference snapshot", e);
        }
    }
}
