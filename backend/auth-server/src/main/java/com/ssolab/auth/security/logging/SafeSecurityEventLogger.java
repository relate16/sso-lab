package com.ssolab.auth.security.logging;

import com.ssolab.auth.security.ratelimit.RateLimitAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SafeSecurityEventLogger {
    private static final Logger LOG = LoggerFactory.getLogger(SafeSecurityEventLogger.class);

    public void rateLimited(RateLimitAction action) {
        LOG.warn("security_event=rate_limited action={}", action.name());
    }

    public void turnstileRejected(String reason) {
        LOG.warn("security_event=turnstile_rejected reason={}",
            SensitiveDataMasker.mask(reason));
    }
}
