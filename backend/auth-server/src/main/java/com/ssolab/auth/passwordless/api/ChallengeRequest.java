package com.ssolab.auth.passwordless.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ChallengeRequest(
    @NotNull UUID challengeId,
    @Size(max = 2048) String turnstileToken
) {
    public ChallengeRequest(UUID challengeId) {
        this(challengeId, null);
    }

    @Override
    public String toString() {
        return "ChallengeRequest[challengeId=" + challengeId + ", turnstileToken=REDACTED]";
    }
}
