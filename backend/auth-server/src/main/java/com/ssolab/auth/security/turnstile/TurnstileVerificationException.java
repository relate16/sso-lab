package com.ssolab.auth.security.turnstile;

public class TurnstileVerificationException extends RuntimeException {
    public TurnstileVerificationException() {
        super("human verification failed");
    }
}
