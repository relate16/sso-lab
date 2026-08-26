package com.ssolab.auth.passwordless.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record ChallengeCodeRequest(
    @NotNull UUID challengeId,
    @Pattern(regexp = "[0-9]{6}") String code
) {

    public char[] copyCode() {
        return code == null ? new char[0] : code.toCharArray();
    }

    @Override
    public String toString() {
        return "ChallengeCodeRequest[challengeId=" + challengeId + ", code=REDACTED]";
    }
}
