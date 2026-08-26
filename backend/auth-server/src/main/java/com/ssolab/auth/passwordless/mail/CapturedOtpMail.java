package com.ssolab.auth.passwordless.mail;

import java.time.Instant;
import java.util.UUID;

public record CapturedOtpMail(
    UUID challengeId,
    OtpMailPurpose purpose,
    String code,
    Instant expiresAt
) {

    @Override
    public String toString() {
        return "CapturedOtpMail[challengeId=" + challengeId + ", purpose=" + purpose
            + ", code=REDACTED, expiresAt=" + expiresAt + "]";
    }
}
