package id.xyz.parkease.config;

import id.xyz.parkease.service.PaymentAdapter;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Fail-closed in production: the in-process adapter is excluded via
 * {@code @Profile("!prod")}, so a real PaymentAdapter must be wired. If none is
 * present, startup fails here rather than silently accepting payments.
 */
@Configuration
@Profile("prod")
public class PaymentAdapterGuardConfiguration {

    public PaymentAdapterGuardConfiguration(List<PaymentAdapter> paymentAdapters) {
        if (paymentAdapters.isEmpty()) {
            throw new IllegalStateException(
                    "no production PaymentAdapter is configured; refusing to start with an unauthenticated payment path");
        }
    }
}
