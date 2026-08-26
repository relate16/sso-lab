package com.ssolab.hr.logout;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.ssolab.hr.config.OidcBffProperties;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class BackChannelLogoutService {
    private static final String EVENT = "http://schemas.openid.net/event/backchannel-logout";
    private final JwtDecoder decoder;
    private final BffSessionRegistry sessions;
    private final ConcurrentHashMap<String, Instant> consumed = new ConcurrentHashMap<>();

    @Autowired
    public BackChannelLogoutService(OidcBffProperties properties, BffSessionRegistry sessions) {
        this(decoder(properties), sessions);
    }

    BackChannelLogoutService(JwtDecoder decoder, BffSessionRegistry sessions) {
        this.decoder = decoder; this.sessions = sessions;
    }

    private static JwtDecoder decoder(OidcBffProperties properties) {
        NimbusJwtDecoder value = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri().toString())
            .validateType(false)
            .jwtProcessorCustomizer(processor -> processor.setJWSTypeVerifier(
                new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType("logout+jwt"))))
            .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        value.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(),
            new JwtIssuerValidator(properties.issuer().toString()), jwt ->
                jwt.getAudience().contains(properties.clientId())
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"))));
        return value;
    }

    public int logout(String token) {
        Jwt jwt = decoder.decode(token);
        Object events = jwt.getClaim("events");
        String sid = jwt.getClaimAsString("sid"); String subject = jwt.getSubject();
        if (!"logout+jwt".equals(jwt.getHeaders().get("typ"))
            || !(events instanceof Map<?, ?> map) || !map.containsKey(EVENT)
            || jwt.hasClaim("nonce") || jwt.getId() == null
            || jwt.getIssuedAt() == null || jwt.getExpiresAt() == null
            || ((sid == null || sid.isBlank()) && (subject == null || subject.isBlank()))) {
            throw new JwtException("invalid logout token");
        }
        consumed.entrySet().removeIf(entry -> entry.getValue().isBefore(Instant.now()));
        if (consumed.putIfAbsent(jwt.getId(), jwt.getExpiresAt()) != null) {
            throw new JwtException("logout token was already used");
        }
        return sessions.invalidate(sid, subject);
    }
}
