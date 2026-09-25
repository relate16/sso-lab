package com.ssolab.gallery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class GallerySecurityConfig {
    @Bean
    SecurityFilterChain gallerySecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/api/v1/csrf").permitAll()
                .requestMatchers("/api/v1/gallery/studio/**").denyAll()
                .requestMatchers("/api/v1/gallery/**").permitAll()
                .anyRequest().denyAll())
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);
        SecurityHeaders.apply(http);
        return http.build();
    }
}
