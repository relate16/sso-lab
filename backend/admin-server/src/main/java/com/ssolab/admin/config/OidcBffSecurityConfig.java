package com.ssolab.admin.config;

import com.ssolab.admin.internal.InternalAdminProperties;
import java.util.LinkedHashSet;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.core.AuthenticationMethod;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import com.ssolab.admin.logout.BffSessionRegistry;

@Configuration
@EnableConfigurationProperties({OidcBffProperties.class, InternalAdminProperties.class})
public class OidcBffSecurityConfig {
    @Bean ClientRegistrationRepository clientRegistrationRepository(OidcBffProperties p) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(p.registrationId())
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
            .userNameAttributeName("sub").clientName("SSO Lab Admin").build();
        return new InMemoryClientRegistrationRepository(registration);
    }
    @Bean SecurityFilterChain oidcBffSecurityFilterChain(HttpSecurity http,
        ClientRegistrationRepository registrations, OidcBffProperties p,
        BffSessionRegistry sessionRegistry) throws Exception {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations);
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        var logoutSuccess = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        logoutSuccess.setPostLogoutRedirectUri(p.postLogoutRedirectUri().toString());
        var loginSuccess = new SavedRequestAwareAuthenticationSuccessHandler();
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/**").permitAll()
                .requestMatchers("/internal/oidc/backchannel-logout").permitAll()
                .requestMatchers("/api/v1/csrf").permitAll()
                .requestMatchers("/api/v1/session", "/api/v1/admin/**")
                    .access((authentication, context) ->
                        new AuthorizationDecision(isQualifiedAdmin(authentication.get())))
                .anyRequest().denyAll())
            .oauth2Login(login -> login.authorizationEndpoint(endpoint -> endpoint
                    .authorizationRequestResolver(resolver))
                .successHandler((request, response, authentication) -> {
                    sessionRegistry.register(request.getSession(),
                        (OidcUser) authentication.getPrincipal());
                    loginSuccess.onAuthenticationSuccess(request, response, authentication);
                })
                .userInfoEndpoint(userInfo -> userInfo.userAuthoritiesMapper(authorities -> {
                    var mapped = new LinkedHashSet<GrantedAuthority>(authorities);
                    authorities.stream().filter(OidcUserAuthority.class::isInstance)
                        .map(OidcUserAuthority.class::cast)
                        .map(OidcUserAuthority::getAttributes)
                        .map(attributes -> attributes.get("roles"))
                        .filter(Iterable.class::isInstance)
                        .forEach(value -> ((Iterable<?>) value).forEach(role -> {
                            if (role instanceof String roleName) {
                                mapped.add(new SimpleGrantedAuthority("ROLE_" + roleName));
                            }
                        }));
                    return mapped;
                }))).logout(logout -> logout.logoutUrl("/api/v1/logout")
                    .logoutSuccessHandler(logoutSuccess))
            .csrf(csrf -> csrf.ignoringRequestMatchers(request ->
                "POST".equals(request.getMethod())
                    && "/internal/oidc/backchannel-logout".equals(request.getServletPath())))
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable);
        SecurityHeaders.apply(http);
        return http.build();
    }

    private boolean isQualifiedAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            return false;
        }
        if (!(authentication.getPrincipal()
            instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser user)) {
            return false;
        }
        return "urn:jb:loa:1".equals(user.getClaimAsString("acr"));
    }
}
