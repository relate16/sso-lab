package com.ssolab.auth.testsupport;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sso.test-support")
public record TestSupportProperties(String apiKey) {

    public void requireValid() {
        if (apiKey == null || apiKey.length() < 16) {
            throw new IllegalArgumentException("test support API key must contain at least 16 characters");
        }
    }
}
