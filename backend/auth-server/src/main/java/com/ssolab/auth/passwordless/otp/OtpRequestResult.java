package com.ssolab.auth.passwordless.otp;

import java.time.Instant;
import java.util.UUID;

public record OtpRequestResult(
    OtpVerificationStatus status,
    UUID challengeId,
    Instant expiresAt,
    Instant resendAvailableAt
) {

    public static OtpRequestResult sent(OtpDelivery delivery) {
        return new OtpRequestResult(
            OtpVerificationStatus.SUCCESS,
            delivery.challengeId(),
            delivery.expiresAt(),
            delivery.resendAvailableAt()
        );
    }

    public static OtpRequestResult failed(OtpVerificationStatus status, UUID challengeId) {
        return new OtpRequestResult(status, challengeId, null, null);
    }
}
