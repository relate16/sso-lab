package com.ssolab.auth.passwordless.mail;

public interface VerificationMailSender {

    void sendOtp(OtpMailMessage message);
}
