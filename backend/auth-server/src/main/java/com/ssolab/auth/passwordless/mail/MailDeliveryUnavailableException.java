package com.ssolab.auth.passwordless.mail;

public class MailDeliveryUnavailableException extends RuntimeException {

    public MailDeliveryUnavailableException() {
        super("verification mail delivery is not configured");
    }

    public MailDeliveryUnavailableException(Throwable cause) {
        super("verification mail delivery is unavailable", cause);
    }
}
