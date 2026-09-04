package com.bloodlink.donor.service;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes "now" a dependency rather than a static call.
 *
 * <p>{@link EligibilityCalculator} answers a question about today's date, so the
 * only way to test its boundary honestly is to be able to say what today is.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock(EligibilityProperties properties) {
        return Clock.system(properties.zone());
    }
}
