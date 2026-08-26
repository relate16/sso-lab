package com.ssolab.auth.security.turnstile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssolab.auth.secret.SecretProviderRegistry;
import com.ssolab.auth.secret.SecretProviderType;
import com.ssolab.auth.secret.SecretValue;
import com.ssolab.auth.security.logging.SafeSecurityEventLogger;
import java.net.URI;
import org.junit.jupiter.api.Test;

class TurnstileServiceTest {
    @Test
    void disabledModeDoesNotRequireTokenOrSecret() {
        SecretProviderRegistry secrets = mock(SecretProviderRegistry.class);
        TurnstileClient client = mock(TurnstileClient.class);
        TurnstileService service = service(false, secrets, client);

        assertThatCode(() -> service.verifyRequired(null, "127.0.0.1"))
            .doesNotThrowAnyException();
        verify(secrets, never()).getSecret(any());
        verify(client, never()).verify(any(), any(), any());
    }

    @Test
    void enabledModeAcceptsOnlySuccessfulServerSideVerification() {
        SecretProviderRegistry secrets = mock(SecretProviderRegistry.class);
        TurnstileClient client = mock(TurnstileClient.class);
        when(secrets.getSecret(any())).thenAnswer(ignored -> SecretValue.of("test-secret"));
        when(client.verify(any(), any(), any())).thenReturn(true, false);
        TurnstileService service = service(true, secrets, client);

        assertThatCode(() -> service.verifyRequired("valid-test-response", "127.0.0.1"))
            .doesNotThrowAnyException();
        assertThatThrownBy(() ->
            service.verifyRequired("invalid-test-response", "127.0.0.1"))
            .isInstanceOf(TurnstileVerificationException.class)
            .hasMessage("human verification failed");
        assertThatThrownBy(() -> service.verifyRequired(null, "127.0.0.1"))
            .isInstanceOf(TurnstileVerificationException.class);
    }

    private TurnstileService service(
        boolean enabled,
        SecretProviderRegistry secrets,
        TurnstileClient client
    ) {
        return new TurnstileService(
            new TurnstileProperties(enabled,
                URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify"),
                SecretProviderType.ENVIRONMENT, "TURNSTILE_SECRET_KEY"),
            secrets,
            client,
            mock(SafeSecurityEventLogger.class)
        );
    }
}
