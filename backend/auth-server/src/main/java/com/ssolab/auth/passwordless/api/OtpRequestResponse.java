package com.ssolab.auth.passwordless.api;

import com.ssolab.auth.passwordless.otp.OtpRequestResult;
import java.time.Instant;
import java.util.UUID;

public record OtpRequestResponse(
    UUID challengeId,
    Instant expiresAt,
    Instant resendAvailableAt,
    String message
) {

    public static OtpRequestResponse from(OtpRequestResult result) {
        return new OtpRequestResponse(
            result.challengeId(),
            result.expiresAt(),
            result.resendAvailableAt(),
            "입력 정보가 유효하다면 인증 메일이 발송되었습니다."
        );
    }
}
