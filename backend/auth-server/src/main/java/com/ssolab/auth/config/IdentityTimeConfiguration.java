package com.ssolab.auth.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdentityTimeConfiguration {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }
}
