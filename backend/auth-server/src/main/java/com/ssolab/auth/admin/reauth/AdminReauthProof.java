package com.ssolab.auth.admin.reauth;

import java.time.Instant;

public record AdminReauthProof(String value, Instant expiresAt) {
    @Override
    public String toString() {
        return "AdminReauthProof[value=<redacted>, expiresAt=" + expiresAt + "]";
    }
}
