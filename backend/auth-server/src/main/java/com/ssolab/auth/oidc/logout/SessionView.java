package com.ssolab.auth.oidc.logout;

import java.time.Instant;
import java.util.UUID;

public record SessionView(UUID id, String device, String authenticationMethod,
    Instant loginAt, Instant lastActivityAt, Instant expiresAt, boolean current) {
}
