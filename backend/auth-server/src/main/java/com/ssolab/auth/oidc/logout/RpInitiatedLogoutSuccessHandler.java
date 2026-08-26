package com.ssolab.auth.oidc.logout;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.oidc.web.authentication.OidcLogoutAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public final class RpInitiatedLogoutSuccessHandler implements AuthenticationSuccessHandler {
    private final LogoutCoordinator logout;
    private final OidcLogoutAuthenticationSuccessHandler delegate =
        new OidcLogoutAuthenticationSuccessHandler();

    public RpInitiatedLogoutSuccessHandler(LogoutCoordinator logout) {
        this.logout = logout;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
        HttpServletResponse response, Authentication authentication)
        throws IOException, ServletException {
        OidcLogoutAuthenticationToken token = (OidcLogoutAuthenticationToken) authentication;
        UUID userId = UUID.fromString(token.getIdToken().getSubject());
        if (token.getSessionId() != null && !token.getSessionId().isBlank()) {
            logout.logoutOne(userId, token.getSessionId(), token.getSessionId());
        } else {
            logout.logoutByOidcSid(userId, token.getIdToken().getClaimAsString("sid"));
        }
        delegate.onAuthenticationSuccess(request, response, authentication);
    }
}
