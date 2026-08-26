package com.ssolab.auth.passwordless.mail;

public class UnavailableVerificationMailSender implements VerificationMailSender {

    @Override
    public void sendOtp(OtpMailMessage message) {
        throw new MailDeliveryUnavailableException();
    }
}
