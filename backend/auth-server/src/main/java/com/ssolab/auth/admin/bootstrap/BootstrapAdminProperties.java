package com.ssolab.auth.admin.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sso.bootstrap-admin")
public record BootstrapAdminProperties(boolean enabled, String email) {

    public BootstrapAdminProperties {
        if (enabled && (email == null || email.isBlank())) {
            throw new IllegalArgumentException(
                "bootstrap admin email must be configured when bootstrap is enabled"
            );
        }
    }
}
