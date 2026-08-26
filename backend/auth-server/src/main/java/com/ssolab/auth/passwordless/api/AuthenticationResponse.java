package com.ssolab.auth.passwordless.api;

public record AuthenticationResponse(
    boolean authenticated,
    String authenticationMethod,
    String continuationPath
) {
}
