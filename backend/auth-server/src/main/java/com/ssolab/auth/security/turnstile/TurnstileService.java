package com.ssolab.auth.security.turnstile;

import com.ssolab.auth.secret.SecretProviderRegistry;
import com.ssolab.auth.secret.SecretValue;
import com.ssolab.auth.security.logging.SafeSecurityEventLogger;
import java.util.Arrays;
import org.springframework.stereotype.Service;

@Service
public class TurnstileService {
    private final TurnstileProperties properties;
    private final SecretProviderRegistry secrets;
    private final TurnstileClient client;
    private final SafeSecurityEventLogger securityLog;

    public TurnstileService(
        TurnstileProperties properties,
        SecretProviderRegistry secrets,
        TurnstileClient client,
        SafeSecurityEventLogger securityLog
    ) {
        this.properties = properties;
        this.secrets = secrets;
        this.client = client;
        this.securityLog = securityLog;
    }

    public void verifyRequired(String responseToken, String remoteAddress) {
        if (!properties.enabled()) {
            return;
        }
        if (responseToken == null || responseToken.isBlank()) {
            securityLog.turnstileRejected("missing_response");
            throw new TurnstileVerificationException();
        }
        try (SecretValue secret = secrets.getSecret(properties.secret())) {
            char[] secretChars = secret.copy();
            try {
                if (!client.verify(secretChars, responseToken, remoteAddress)) {
                    securityLog.turnstileRejected("verification_rejected");
                    throw new TurnstileVerificationException();
                }
            } finally {
                Arrays.fill(secretChars, '\0');
            }
        } catch (TurnstileVerificationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            securityLog.turnstileRejected("verification_unavailable");
            throw new TurnstileVerificationException();
        }
    }
}
