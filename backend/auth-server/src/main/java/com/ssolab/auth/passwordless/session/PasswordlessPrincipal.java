package com.ssolab.auth.passwordless.session;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public record PasswordlessPrincipal(
    UUID userId,
    String loginId,
    List<String> roles,
    SessionScope scope,
    AuthenticationMethod authenticationMethod,
    Instant authenticatedAt,
    String sessionId
) implements Principal, Serializable {

    public PasswordlessPrincipal {
        roles = new ArrayList<>(roles);
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}
