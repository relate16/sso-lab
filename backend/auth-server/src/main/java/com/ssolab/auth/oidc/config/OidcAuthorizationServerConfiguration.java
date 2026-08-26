package com.ssolab.auth.oidc.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.ssolab.auth.oidc.client.ManagedRegisteredClientRepository;
import com.ssolab.auth.config.SecurityHeaders;
import com.ssolab.auth.oidc.crypto.OidcSigningKeyLoader;
import com.ssolab.auth.oidc.logout.LogoutCoordinator;
import com.ssolab.auth.oidc.logout.RpInitiatedLogoutSuccessHandler;
import com.ssolab.auth.passwordless.session.PasswordlessSessionValidationFilter;
import com.ssolab.auth.passwordless.session.PasswordlessPrincipal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.Customizer;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration
public class OidcAuthorizationServerConfiguration {

    private static final List<String> USER_INFO_CLAIMS = List.of(
        "sub", "userId", "preferred_username", "username", "name", "email",
        "roles", "groups", "amr", "acr", "auth_time", "sid"
    );

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(
        HttpSecurity http,
        OidcProperties properties,
        PasswordlessSessionValidationFilter sessionValidationFilter,
        LogoutCoordinator logoutCoordinator
    ) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer =
            new OAuth2AuthorizationServerConfigurer();
        http
            .securityMatcher(authorizationServer.getEndpointsMatcher())
            .with(authorizationServer, server -> server
                .oidc(oidc -> oidc
                    .providerConfigurationEndpoint(endpoint -> endpoint
                        .providerConfigurationCustomizer(configuration -> configuration
                            .claim("backchannel_logout_supported", true)
                            .claim("backchannel_logout_session_supported", true)))
                    .logoutEndpoint(logout -> logout.logoutResponseHandler(
                        new RpInitiatedLogoutSuccessHandler(logoutCoordinator)))
                    .userInfoEndpoint(userInfo -> userInfo.userInfoMapper(context -> {
                        OidcIdToken idToken = context.getAuthorization()
                            .getToken(OidcIdToken.class)
                            .getToken();
                        Map<String, Object> claims = new LinkedHashMap<>();
                        USER_INFO_CLAIMS.forEach(name -> {
                            Object value = idToken.getClaims().get(name);
                            if (value != null) {
                                claims.put(name, value);
                            }
                        });
                        return new OidcUserInfo(claims);
                    })))
            )
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                new LoginUrlAuthenticationEntryPoint(properties.getLoginPageUri())
            ))
            .addFilterAfter(sessionValidationFilter, SecurityContextHolderFilter.class);
        SecurityHeaders.apply(http);
        return http.build();
    }

    @Bean
    ManagedRegisteredClientRepository registeredClientRepository(JdbcOperations jdbcOperations) {
        return new ManagedRegisteredClientRepository(jdbcOperations);
    }

    @Bean
    OAuth2AuthorizationService authorizationService(
        JdbcOperations jdbcOperations,
        RegisteredClientRepository registeredClientRepository
    ) {
        var validator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType(PasswordlessPrincipal.class)
            .allowIfSubType(ArrayList.class)
            .allowIfSubType(Long.class)
            .allowIfSubType(Date.class);
        JsonMapper jsonMapper = JsonMapper.builder()
            .addModules(SecurityJacksonModules.getModules(
                PasswordlessPrincipal.class.getClassLoader(), validator
            ))
            .build();
        JdbcOAuth2AuthorizationService service = new JdbcOAuth2AuthorizationService(
            jdbcOperations, registeredClientRepository
        );
        service.setAuthorizationRowMapper(
            new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(
                registeredClientRepository, jsonMapper
            )
        );
        service.setAuthorizationParametersMapper(
            new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationParametersMapper(
                jsonMapper
            )
        );
        return service;
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(
        JdbcOperations jdbcOperations,
        RegisteredClientRepository registeredClientRepository
    ) {
        return new JdbcOAuth2AuthorizationConsentService(
            jdbcOperations, registeredClientRepository
        );
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(OidcSigningKeyLoader loader) {
        RSAKey rsaKey = loader.load();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(OidcProperties properties) {
        properties.validate();
        return AuthorizationServerSettings.builder()
            .issuer(properties.getIssuer().toString())
            .build();
    }

    @Bean
    PasswordEncoder oidcClientSecretPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
