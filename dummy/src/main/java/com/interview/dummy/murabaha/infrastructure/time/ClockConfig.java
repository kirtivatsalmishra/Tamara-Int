package com.interview.dummy.murabaha.infrastructure.time;

import com.interview.dummy.murabaha.infrastructure.config.MurabahaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Provides the single application-wide {@link Clock} bean pinned to the
 * configured business time zone.
 *
 * <p>Tests override this bean to obtain deterministic dates.
 */
@Configuration
public class ClockConfig {

    private final MurabahaProperties properties;

    public ClockConfig(MurabahaProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of(properties.getBusinessZone()));
    }
}