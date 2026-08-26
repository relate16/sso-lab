package com.ssolab.auth.oidc.claims;

import com.ssolab.auth.oidc.config.OidcProperties;
import com.ssolab.auth.passwordless.session.PasswordlessPrincipal;
import com.ssolab.auth.oidc.logout.OidcClientSessionService;
import java.util.Map;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

@Component
public class OidcTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final OidcIdentityClaimsService claimsService;
    private final OidcProperties properties;
    private final OidcClientSessionService clientSessionService;

    public OidcTokenCustomizer(
        OidcIdentityClaimsService claimsService,
        OidcProperties properties,
        OidcClientSessionService clientSessionService
    ) {
        this.claimsService = claimsService;
        this.properties = properties;
        this.clientSessionService = clientSessionService;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
        boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
        if (!accessToken && !idToken) {
            return;
        }
        if (idToken) {
            context.getClaims().expiresAt(
                context.getClaims().build().getIssuedAt().plus(properties.getIdTokenTtl())
            );
        }
        Object principal = context.getPrincipal().getPrincipal();
        if (!(principal instanceof PasswordlessPrincipal passwordlessPrincipal)) {
            throw new IllegalStateException("OIDC user token requires passwordless principal");
        }
        Map<String, Object> claims = claimsService
            .load(passwordlessPrincipal, context.getAuthorizedScopes())
            .values();
        context.getClaims().claims(existing -> existing.putAll(claims));
        if (idToken) {
            clientSessionService.link(passwordlessPrincipal,
                context.getRegisteredClient(), context.getAuthorization());
        }
    }
}
