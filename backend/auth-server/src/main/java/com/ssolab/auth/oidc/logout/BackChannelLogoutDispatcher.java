package com.ssolab.auth.oidc.logout;

import com.ssolab.auth.oidc.config.OidcProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

@Component
class BackChannelLogoutDispatcher {
    static final String LOGOUT_EVENT = "http://schemas.openid.net/event/backchannel-logout";
    private static final Logger LOG = LoggerFactory.getLogger(BackChannelLogoutDispatcher.class);
    private final JwtEncoder encoder;
    private final OidcProperties properties;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2)).build();

    BackChannelLogoutDispatcher(JwtEncoder encoder, OidcProperties properties) {
        this.encoder = encoder; this.properties = properties;
    }

    void dispatch(List<BackChannelLogoutCommand> commands) {
        commands.forEach(this::dispatch);
    }

    private void dispatch(BackChannelLogoutCommand command) {
        String token = token(command);
        String body = "logout_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(command.endpoint())
            .timeout(Duration.ofSeconds(3))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status < 200 || status >= 300) {
                LOG.warn("Back-channel logout failed for client {} with status {}",
                    command.clientId(), status);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOG.warn("Back-channel logout interrupted for client {}", command.clientId());
        } catch (Exception exception) {
            LOG.warn("Back-channel logout unavailable for client {}", command.clientId());
        }
    }

    private String token(BackChannelLogoutCommand command) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(properties.getIssuer().toString())
            .subject(command.subject()).audience(List.of(command.clientId()))
            .issuedAt(now).expiresAt(now.plus(properties.getLogoutTokenTtl()))
            .id(UUID.randomUUID().toString())
            .claim("events", Map.of(LOGOUT_EVENT, Map.of()))
            .claim("sid", command.sid()).build();
        JwsHeader headers = JwsHeader.with(SignatureAlgorithm.RS256)
            .type("logout+jwt").keyId(properties.getSigning().getKeyId()).build();
        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }
}
