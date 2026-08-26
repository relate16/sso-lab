package com.ssolab.auth.passwordless.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecoveryStartRequest(
    @NotBlank @Size(max = 30) String userId,
    @NotBlank @Size(max = 64) String recoveryCode,
    @Size(max = 2048) String turnstileToken
) {

    public RecoveryStartRequest(String userId, String recoveryCode) {
        this(userId, recoveryCode, null);
    }

    @Override
    public String toString() {
        return "RecoveryStartRequest[userId=REDACTED, recoveryCode=REDACTED, "
            + "turnstileToken=REDACTED]";
    }
}
