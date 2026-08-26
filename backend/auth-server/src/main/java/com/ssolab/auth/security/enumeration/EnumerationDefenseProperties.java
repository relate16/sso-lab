package com.ssolab.auth.security.enumeration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sso.security.enumeration")
public record EnumerationDefenseProperties(Duration minimumResponseTime) {
    public EnumerationDefenseProperties {
        if (minimumResponseTime == null || minimumResponseTime.isNegative()
            || minimumResponseTime.compareTo(Duration.ofSeconds(2)) > 0) {
            throw new IllegalArgumentException(
                "enumeration minimum response time must be between 0 and 2 seconds");
        }
    }
}
