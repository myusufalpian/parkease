package id.xyz.parkease.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.xyz.parkease.dto.PricingSnapshot;
import java.util.Objects;
import org.springframework.util.StringUtils;

public record PricingSnapshotMapper(ObjectMapper objectMapper) {

    private static final String BLANK_MESSAGE = "pricing snapshot json must not be blank";

    public PricingSnapshotMapper {
        objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public String toJson(PricingSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to serialize pricing snapshot", e);
        }
    }

    public PricingSnapshot fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            throw new IllegalArgumentException(BLANK_MESSAGE);
        }
        try {
            return objectMapper.readValue(json, PricingSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to deserialize pricing snapshot", e);
        }
    }
}
