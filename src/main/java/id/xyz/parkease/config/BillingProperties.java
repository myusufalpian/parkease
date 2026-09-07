package id.xyz.parkease.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "parkease.billing")
public record BillingProperties(
        @Positive long paymentTimeoutMinutes,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal cancellationFeeRate) {
}
