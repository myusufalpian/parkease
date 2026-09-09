package id.xyz.parkease.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "parkease.booking")
public record BookingProperties(
        @Positive long minimumDurationMinutes,
        @Positive long futureHorizonDays) {
}
