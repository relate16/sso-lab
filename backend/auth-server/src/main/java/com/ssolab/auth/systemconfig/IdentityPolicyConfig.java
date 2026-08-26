package com.ssolab.auth.systemconfig;

import java.time.Duration;

public record IdentityPolicyConfig(
    Duration ssoSessionIdleTimeout,
    Duration ssoSessionAbsoluteTimeout,
    Duration accessTokenTtl,
    Duration idTokenTtl,
    Duration refreshTokenTtl,
    Duration adminReauthTtl,
    Duration emailOtpTtl,
    int emailOtpMaxAttempts,
    Duration emailOtpResendInterval
) {
}
