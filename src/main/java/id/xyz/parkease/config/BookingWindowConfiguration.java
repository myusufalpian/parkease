package id.xyz.parkease.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BookingProperties.class)
public class BookingWindowConfiguration {
}
