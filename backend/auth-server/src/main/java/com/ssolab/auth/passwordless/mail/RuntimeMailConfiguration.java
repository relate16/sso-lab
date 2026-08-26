package com.ssolab.auth.passwordless.mail;

import com.ssolab.auth.secret.SecretProviderRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!local & !test")
@EnableConfigurationProperties(GmailSmtpProperties.class)
public class RuntimeMailConfiguration {

    @Bean
    VerificationMailSender verificationMailSender(
        GmailSmtpProperties properties,
        SecretProviderRegistry secrets,
        GmailMailTransport transport
    ) {
        if (!properties.enabled()) {
            return new UnavailableVerificationMailSender();
        }
        return new GmailSmtpVerificationMailSender(properties, secrets, transport);
    }
}
