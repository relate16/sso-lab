package com.ssolab.auth.admin.internal;

import com.ssolab.auth.secret.SecretProviderRegistry;
import com.ssolab.auth.secret.SecretValue;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class InternalAdminAuthenticationFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_HEADER = "X-Internal-Client-Id";
    public static final String SERVICE_SECRET_HEADER = "X-Internal-Service-Secret";

    private final InternalAdminProperties properties;
    private final SecretProviderRegistry secrets;

    public InternalAdminAuthenticationFilter(
        InternalAdminProperties properties,
        SecretProviderRegistry secrets
    ) {
        this.properties = properties;
        this.secrets = secrets;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String clientId = request.getHeader(CLIENT_ID_HEADER);
        String presentedSecret = request.getHeader(SERVICE_SECRET_HEADER);
        if (!properties.allowedClientId().equals(clientId)
            || presentedSecret == null || !matches(presentedSecret)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"internal_authentication_required\"}");
            return;
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            clientId,
            null,
            java.util.List.of(new SimpleGrantedAuthority("INTERNAL_ADMIN_SERVICE"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean matches(String presented) {
        try (SecretValue secret = secrets.getSecret(properties.serviceSecret())) {
            char[] expected = secret.copy();
            char[] actual = presented.toCharArray();
            try {
                if (expected.length != actual.length) {
                    return false;
                }
                int difference = 0;
                for (int index = 0; index < expected.length; index++) {
                    difference |= expected[index] ^ actual[index];
                }
                return difference == 0;
            } finally {
                Arrays.fill(expected, '\0');
                Arrays.fill(actual, '\0');
            }
        }
    }
}
