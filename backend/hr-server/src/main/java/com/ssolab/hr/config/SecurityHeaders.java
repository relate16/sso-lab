package com.ssolab.hr.config;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.PermissionsPolicyHeaderWriter;

final class SecurityHeaders {
    private SecurityHeaders() { }
    static void apply(HttpSecurity http) throws Exception {
        http.headers(headers -> headers
            .contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'"))
            .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
            .addHeaderWriter(new PermissionsPolicyHeaderWriter(
                "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
            .frameOptions(frame -> frame.deny())
            .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(false)
                .preload(false).maxAgeInSeconds(31_536_000)));
    }
}
