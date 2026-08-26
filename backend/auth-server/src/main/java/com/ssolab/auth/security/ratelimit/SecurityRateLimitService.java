package com.ssolab.auth.security.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import com.ssolab.auth.security.logging.SafeSecurityEventLogger;
import org.springframework.stereotype.Service;

@Service
public class SecurityRateLimitService {
    private final InMemorySlidingWindowRateLimiter limiter;
    private final SafeSecurityEventLogger securityLog;

    public SecurityRateLimitService(
        InMemorySlidingWindowRateLimiter limiter,
        SafeSecurityEventLogger securityLog
    ) {
        this.limiter = limiter;
        this.securityLog = securityLog;
    }

    public void check(
        RateLimitAction action,
        String remoteAddress,
        String subjectIdentifier,
        String challengeIdentifier
    ) {
        acquire(action, "ip", remoteAddress, action.ipLimit());
        acquire(action, "subject", subjectIdentifier, action.subjectLimit());
        acquire(action, "challenge", challengeIdentifier, action.challengeLimit());
    }

    private void acquire(RateLimitAction action, String dimension, String raw, int limit) {
        if (limit <= 0 || raw == null || raw.isBlank()) {
            return;
        }
        String key = action.name() + ':' + dimension + ':' + fingerprint(raw);
        if (!limiter.tryAcquire(key, limit, action.window())) {
            securityLog.rateLimited(action);
            throw new RateLimitExceededException(action.window());
        }
    }

    static String fingerprint(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
