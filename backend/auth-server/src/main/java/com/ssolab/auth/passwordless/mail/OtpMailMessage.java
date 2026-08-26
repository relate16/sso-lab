package com.ssolab.auth.passwordless.mail;

import com.ssolab.auth.passwordless.crypto.SensitiveCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OtpMailMessage(
    UUID challengeId,
    OtpMailPurpose purpose,
    String recipient,
    SensitiveCode code,
    Instant expiresAt
) {

    public OtpMailMessage {
        Objects.requireNonNull(challengeId);
        Objects.requireNonNull(purpose);
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("OTP recipient is required");
        }
        Objects.requireNonNull(code);
        Objects.requireNonNull(expiresAt);
    }

    @Override
    public String toString() {
        return "OtpMailMessage[challengeId=" + challengeId + ", purpose=" + purpose
            + ", recipient=REDACTED, code=REDACTED, expiresAt=" + expiresAt + "]";
    }
}
