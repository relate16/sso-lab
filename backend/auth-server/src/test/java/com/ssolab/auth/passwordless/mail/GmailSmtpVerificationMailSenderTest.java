package com.ssolab.auth.passwordless.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.ssolab.auth.passwordless.crypto.SensitiveCode;
import com.ssolab.auth.secret.SecretProvider;
import com.ssolab.auth.secret.SecretProviderRegistry;
import com.ssolab.auth.secret.SecretProviderType;
import com.ssolab.auth.secret.SecretReference;
import com.ssolab.auth.secret.SecretValue;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GmailSmtpVerificationMailSenderTest {

    @Test
    void delegatesWithoutLoggingCredentialsAndClearsCopiedPassword() {
        SecretProvider provider = new SecretProvider() {
            @Override
            public SecretProviderType type() {
                return SecretProviderType.ENVIRONMENT;
            }

            @Override
            public SecretValue getSecret(String reference) {
                assertThat(reference).isEqualTo("SMTP_TEST_SECRET");
                return SecretValue.of("smtp-app-password");
            }
        };
        var properties = new GmailSmtpProperties(
            true, "smtp.gmail.com", 587, "sender@example.test", "sender@example.test",
            new SecretReference(SecretProviderType.ENVIRONMENT, "SMTP_TEST_SECRET"),
            Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(2)
        );
        GmailMailTransport transport = mock(GmailMailTransport.class);
        AtomicReference<char[]> copiedPassword = new AtomicReference<>();
        doAnswer(invocation -> {
            copiedPassword.set(invocation.getArgument(4));
            assertThat((char[]) invocation.getArgument(4)).startsWith('s', 'm', 't', 'p');
            assertThat((String) invocation.getArgument(3)).contains("123456");
            return null;
        }).when(transport).send(any(), any(), any(), any(), any());

        var sender = new GmailSmtpVerificationMailSender(
            properties, new SecretProviderRegistry(List.of(provider)), transport
        );
        try (SensitiveCode code = SensitiveCode.of("123456".toCharArray())) {
            sender.sendOtp(new OtpMailMessage(
                UUID.randomUUID(), OtpMailPurpose.LOGIN, "recipient@example.test", code,
                Instant.parse("2026-08-24T12:00:00Z")
            ));
        }

        assertThat(copiedPassword.get()).containsOnly('\0');
    }
}
