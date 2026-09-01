package com.ssolab.auth.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdentityTimeConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "sso.test-support",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
    )
    Clock utcClock() {
        return Clock.systemUTC();
    }
}
