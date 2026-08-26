package com.ssolab.auth.passwordless.mail;

public interface GmailMailTransport {
    void send(
        GmailSmtpProperties properties,
        String recipient,
        String subject,
        String body,
        char[] password
    );
}
