package com.ssolab.auth.security.ratelimit;

import java.time.Duration;

public enum RateLimitAction {
    SIGNUP_START(Duration.ofMinutes(10), 10, 3, 0),
    LOGIN_START(Duration.ofMinutes(10), 30, 0, 0),
    EMAIL_OTP_SEND(Duration.ofMinutes(10), 20, 5, 0),
    EMAIL_OTP_RESEND(Duration.ofMinutes(10), 20, 0, 5),
    EMAIL_OTP_VERIFY(Duration.ofMinutes(10), 40, 0, 5),
    TOTP_VERIFY(Duration.ofMinutes(10), 30, 10, 0),
    RECOVERY_CODE_VERIFY(Duration.ofMinutes(30), 10, 5, 0),
    ADMIN_REAUTH(Duration.ofMinutes(10), 30, 10, 5);

    private final Duration window;
    private final int ipLimit;
    private final int subjectLimit;
    private final int challengeLimit;

    RateLimitAction(Duration window, int ipLimit, int subjectLimit, int challengeLimit) {
        this.window = window;
        this.ipLimit = ipLimit;
        this.subjectLimit = subjectLimit;
        this.challengeLimit = challengeLimit;
    }

    public Duration window() { return window; }
    public int ipLimit() { return ipLimit; }
    public int subjectLimit() { return subjectLimit; }
    public int challengeLimit() { return challengeLimit; }
}
