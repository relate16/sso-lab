package com.ssolab.auth.passwordless.mail;

import com.ssolab.auth.secret.SecretProviderType;
import com.ssolab.auth.secret.SecretReference;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sso.mail.gmail")
public record GmailSmtpProperties(
    boolean enabled,
    String host,
    int port,
    String username,
    String from,
    SecretReference password,
    Duration connectionTimeout,
    Duration readTimeout,
    Duration writeTimeout
) {
    public GmailSmtpProperties {
        if (host == null || host.isBlank() || port < 1 || port > 65535) {
            throw new IllegalArgumentException("Gmail SMTP endpoint is invalid");
        }
        if (password == null) {
            password = new SecretReference(
                SecretProviderType.ENVIRONMENT, "GMAIL_APP_PASSWORD"
            );
        }
        connectionTimeout = requirePositive(connectionTimeout, "connection timeout");
        readTimeout = requirePositive(readTimeout, "read timeout");
        writeTimeout = requirePositive(writeTimeout, "write timeout");
        if (enabled && (username == null || username.isBlank()
            || from == null || from.isBlank())) {
            throw new IllegalArgumentException("Gmail SMTP username/from must be configured");
        }
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("Gmail SMTP " + name + " must be positive");
        }
        return value;
    }
}
