package com.ssolab.auth.passwordless.otp;

import com.ssolab.auth.passwordless.crypto.SensitiveCode;
import com.ssolab.auth.passwordless.mail.OtpMailPurpose;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record OtpDelivery(
    UUID challengeId,
    OtpMailPurpose purpose,
    String recipient,
    SensitiveCode code,
    Instant expiresAt,
    Instant resendAvailableAt
) implements AutoCloseable {

    public Optional<String> recipientIfDeliverable() {
        return Optional.ofNullable(recipient);
    }

    @Override
    public void close() {
        code.close();
    }

    @Override
    public String toString() {
        return "OtpDelivery[challengeId=" + challengeId + ", purpose=" + purpose
            + ", recipient=REDACTED, code=REDACTED, expiresAt=" + expiresAt
            + ", resendAvailableAt=" + resendAvailableAt + "]";
    }
}
