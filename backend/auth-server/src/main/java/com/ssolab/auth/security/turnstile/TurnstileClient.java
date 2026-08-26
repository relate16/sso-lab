package com.ssolab.auth.security.turnstile;

public interface TurnstileClient {
    boolean verify(char[] secret, String responseToken, String remoteAddress);
}
