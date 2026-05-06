package com.interview.dummy.murabaha.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Activates {@link MurabahaProperties} binding without forcing component-scan
 * of a {@code @Component}-annotated POJO.
 */
@Configuration
@EnableConfigurationProperties(MurabahaProperties.class)
public class MurabahaConfiguration {
}
