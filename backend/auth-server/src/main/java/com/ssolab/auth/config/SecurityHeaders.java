package com.ssolab.auth.config;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.PermissionsPolicyHeaderWriter;

public final class SecurityHeaders {
    private static final String CSP = "default-src 'none'; base-uri 'none'; "
        + "frame-ancestors 'none'; form-action 'self'";
    private static final String PERMISSIONS = "camera=(), microphone=(), geolocation=(), "
        + "payment=(), usb=()";

    private SecurityHeaders() {
    }

    public static void apply(HttpSecurity http) throws Exception {
        http.headers(headers -> headers
            .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
            .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
            .addHeaderWriter(new PermissionsPolicyHeaderWriter(PERMISSIONS))
            .frameOptions(frame -> frame.deny())
            .httpStrictTransportSecurity(hsts -> hsts
                .includeSubDomains(false).preload(false).maxAgeInSeconds(31_536_000)));
    }
}
