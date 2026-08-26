package com.ssolab.auth.admin.internal;

import com.ssolab.auth.secret.SecretProviderRegistry;
import com.ssolab.auth.config.SecurityHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class InternalAdminSecurityConfiguration {

    @Bean
    @Order(2)
    SecurityFilterChain internalAdminSecurityFilterChain(
        HttpSecurity http,
        InternalAdminProperties properties,
        SecretProviderRegistry secrets
    ) throws Exception {
        InternalAdminAuthenticationFilter filter =
            new InternalAdminAuthenticationFilter(properties, secrets);
        http
            .securityMatcher("/internal/admin/v1/**")
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().hasAuthority("INTERNAL_ADMIN_SERVICE"))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/internal/admin/v1/**"))
            .addFilterBefore(filter, AnonymousAuthenticationFilter.class);
        SecurityHeaders.apply(http);
        return http.build();
    }
}
