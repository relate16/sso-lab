package com.ssolab.auth.config;

import com.ssolab.auth.passwordless.session.PasswordlessSessionValidationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
public class FoundationSecurityConfig {

    @Bean
    @Order(3)
    SecurityFilterChain foundationSecurityFilterChain(
        HttpSecurity http,
        PasswordlessSessionValidationFilter sessionValidationFilter
    ) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/test-support/**"))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/api/v1/csrf").permitAll()
                .requestMatchers("/api/v1/signup/**", "/api/v1/login/**").permitAll()
                .requestMatchers("/test-support/**").permitAll()
                .requestMatchers("/api/v1/totp/recovery/start").permitAll()
                .requestMatchers("/api/v1/totp/recovery/enroll/**")
                    .hasAuthority("SCOPE_RECOVERY_ONLY")
                .requestMatchers("/api/v1/me/**").hasAuthority("SCOPE_NORMAL")
                .anyRequest().denyAll())
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .addFilterAfter(sessionValidationFilter, SecurityContextHolderFilter.class);

        SecurityHeaders.apply(http);

        return http.build();
    }

    @Bean
    UserDetailsService emptyUserDetailsService() {
        // Phase 1 deliberately exposes no login credentials or authentication endpoint.
        return new InMemoryUserDetailsManager();
    }
}
