package com.ssolab.gallery.config;

import com.ssolab.gallery.logout.BffSessionRegistry;
import java.util.LinkedHashSet;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.AuthenticationMethod;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
@EnableConfigurationProperties(GalleryOidcProperties.class)
public class GallerySecurityConfig {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository(GalleryOidcProperties p) {
        var registration = ClientRegistration.withRegistrationId(p.registrationId())
            .clientId(p.clientId()).clientSecret(p.clientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(p.redirectUri()).scope("openid", "profile", "email")
            .authorizationUri(p.authorizationUri().toString()).tokenUri(p.tokenUri().toString())
            .jwkSetUri(p.jwkSetUri().toString()).issuerUri(p.issuer().toString())
            .userInfoUri(p.userInfoUri().toString())
            .userInfoAuthenticationMethod(AuthenticationMethod.HEADER)
            .providerConfigurationMetadata(Map.of("end_session_endpoint",
                p.issuer().resolve("/connect/logout").toString()))
            .userNameAttributeName("sub").clientName("Quiet Winter Gallery Studio").build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    SecurityFilterChain gallerySecurityFilterChain(HttpSecurity http,
        ClientRegistrationRepository registrations, GalleryOidcProperties properties,
        BffSessionRegistry sessions) throws Exception {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations);
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        var logoutSuccess = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        logoutSuccess.setPostLogoutRedirectUri(properties.postLogoutRedirectUri().toString());
        var loginSuccess = new SimpleUrlAuthenticationSuccessHandler("/studio");

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/**", "/internal/oidc/backchannel-logout").permitAll()
                .requestMatchers("/api/v1/csrf", "/api/v1/session").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/gallery/home", "/api/v1/gallery/artworks/**",
                    "/api/v1/gallery/media/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/gallery/inquiries").permitAll()
                .requestMatchers("/api/v1/gallery/studio/**").hasRole("ADMIN")
                .anyRequest().denyAll())
            .oauth2Login(login -> login.authorizationEndpoint(endpoint -> endpoint
                    .authorizationRequestResolver(resolver))
                .successHandler((request, response, authentication) -> {
                    sessions.register(request.getSession(), (OidcUser) authentication.getPrincipal());
                    loginSuccess.onAuthenticationSuccess(request, response, authentication);
                })
                .userInfoEndpoint(info -> info.userAuthoritiesMapper(authorities -> {
                    var mapped = new LinkedHashSet<GrantedAuthority>(authorities);
                    authorities.stream().filter(OidcUserAuthority.class::isInstance)
                        .map(OidcUserAuthority.class::cast).map(OidcUserAuthority::getAttributes)
                        .map(attributes -> attributes.get("roles")).filter(Iterable.class::isInstance)
                        .forEach(value -> ((Iterable<?>) value).forEach(role -> {
                            if (role instanceof String name) mapped.add(new SimpleGrantedAuthority("ROLE_" + name));
                        }));
                    return mapped;
                })))
            .logout(logout -> logout.logoutUrl("/api/v1/logout").logoutSuccessHandler(logoutSuccess))
            .csrf(csrf -> csrf.ignoringRequestMatchers(request -> "POST".equals(request.getMethod())
                && "/internal/oidc/backchannel-logout".equals(request.getServletPath())))
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable);
        SecurityHeaders.apply(http);
        return http.build();
    }
}
