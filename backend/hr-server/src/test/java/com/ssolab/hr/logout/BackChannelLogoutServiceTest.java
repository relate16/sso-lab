package com.ssolab.hr.logout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ssolab.hr.config.OidcBffProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class BackChannelLogoutServiceTest {
    private static final String LOGOUT_EVENT =
        "http://schemas.openid.net/event/backchannel-logout";

    @Test
    void acceptsValidatedLogoutTokenInvalidatesSidAndRejectsReplay() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        BffSessionRegistry registry = new BffSessionRegistry();
        MockHttpSession session = new MockHttpSession();
        Instant now = Instant.now();
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token-not-logged")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).subject("subject-1")
            .claim("sid", "sid-1").build();
        registry.register(session, new DefaultOidcUser(
            List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken));

        Jwt logout = Jwt.withTokenValue("logout-token-not-logged")
            .header("alg", "RS256").header("typ", "logout+jwt")
            .issuer("http://localhost:8080").subject("subject-1")
            .audience(List.of("hr-client")).issuedAt(now).expiresAt(now.plusSeconds(120))
            .claim("jti", "logout-jti-1")
            .claim("events", Map.of(
                "http://schemas.openid.net/event/backchannel-logout", Map.of()))
            .claim("sid", "sid-1").build();
        when(decoder.decode("valid")).thenReturn(logout);
        BackChannelLogoutService service = new BackChannelLogoutService(decoder, registry);

        assertThat(service.logout("valid")).isEqualTo(1);
        assertThat(session.isInvalid()).isTrue();
        assertThatThrownBy(() -> service.logout("valid")).isInstanceOf(JwtException.class);
    }

    @Test
    void signatureOrClaimValidationFailureDoesNotInvalidateSession() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("bad-signature")).thenThrow(new JwtException("invalid signature"));
        BffSessionRegistry registry = new BffSessionRegistry();
        MockHttpSession session = new MockHttpSession();
        Instant now = Instant.now();
        registry.register(session, new DefaultOidcUser(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            OidcIdToken.withTokenValue("id-token-not-logged").issuedAt(now)
                .expiresAt(now.plusSeconds(300)).subject("subject-1")
                .claim("sid", "sid-1").build()));
        BackChannelLogoutService service = new BackChannelLogoutService(decoder, registry);

        assertThatThrownBy(() -> service.logout("bad-signature"))
            .isInstanceOf(JwtException.class);
        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void verifiesStandardLogoutJwtSignatureIssuerAudienceAndExpiry() throws Exception {
        KeyPair signingKey = rsaKeyPair();
        RSAKey publicJwk = new RSAKey.Builder((RSAPublicKey) signingKey.getPublic())
            .keyID("logout-test-key")
            .build();
        HttpServer jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/jwks", exchange -> {
            byte[] body = new JWKSet(publicJwk).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        jwksServer.start();

        try {
            URI issuer = URI.create("http://issuer.example.test");
            URI jwkSetUri = URI.create(
                "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks");
            OidcBffProperties properties = properties(issuer, jwkSetUri);
            BffSessionRegistry registry = new BffSessionRegistry();
            MockHttpSession session = registeredSession(registry, "sid-rsa", "subject-rsa");
            BackChannelLogoutService service = new BackChannelLogoutService(properties, registry);
            Instant now = Instant.now();

            String valid = signedLogoutToken(signingKey, issuer.toString(), "hr-client",
                "valid-jti", now, now.plusSeconds(120));
            assertThat(service.logout(valid)).isEqualTo(1);
            assertThat(session.isInvalid()).isTrue();

            KeyPair wrongSigningKey = rsaKeyPair();
            assertThatThrownBy(() -> service.logout(signedLogoutToken(wrongSigningKey,
                issuer.toString(), "hr-client", "wrong-signature-jti", now,
                now.plusSeconds(120)))).isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> service.logout(signedLogoutToken(signingKey,
                "http://wrong-issuer.example.test", "hr-client", "wrong-issuer-jti", now,
                now.plusSeconds(120)))).isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> service.logout(signedLogoutToken(signingKey,
                issuer.toString(), "wrong-client", "wrong-audience-jti", now,
                now.plusSeconds(120)))).isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> service.logout(signedLogoutToken(signingKey,
                issuer.toString(), "hr-client", "expired-jti", now.minusSeconds(600),
                now.minusSeconds(300)))).isInstanceOf(JwtException.class);
        } finally {
            jwksServer.stop(0);
        }
    }

    private static OidcBffProperties properties(URI issuer, URI jwkSetUri) {
        return new OidcBffProperties("hr", "hr-client", "test-client-secret", null, issuer,
            issuer.resolve("/oauth2/authorize"), issuer.resolve("/oauth2/token"), jwkSetUri,
            issuer.resolve("/userinfo"), "http://localhost/login/oauth2/code/hr-client",
            URI.create("http://localhost:8081/"));
    }

    private static MockHttpSession registeredSession(BffSessionRegistry registry, String sid,
        String subject) {
        MockHttpSession session = new MockHttpSession();
        Instant now = Instant.now();
        registry.register(session, new DefaultOidcUser(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            OidcIdToken.withTokenValue("id-token-not-logged").issuedAt(now)
                .expiresAt(now.plusSeconds(300)).subject(subject).claim("sid", sid).build()));
        return session;
    }

    private static String signedLogoutToken(KeyPair keyPair, String issuer, String audience,
        String jwtId, Instant issuedAt, Instant expiresAt) throws Exception {
        SignedJWT token = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("logout+jwt"))
                .keyID("logout-test-key")
                .build(),
            new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("subject-rsa")
                .audience(audience)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID(jwtId)
                .claim("events", Map.of(LOGOUT_EVENT, Map.of()))
                .claim("sid", "sid-rsa")
                .build());
        token.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        return token.serialize();
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
