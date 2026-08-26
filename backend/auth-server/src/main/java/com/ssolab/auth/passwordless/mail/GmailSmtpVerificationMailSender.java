package com.ssolab.auth.passwordless.mail;

import com.ssolab.auth.secret.SecretProviderRegistry;
import com.ssolab.auth.secret.SecretValue;
import java.util.Arrays;

public class GmailSmtpVerificationMailSender implements VerificationMailSender {
    private final GmailSmtpProperties properties;
    private final SecretProviderRegistry secrets;
    private final GmailMailTransport transport;

    public GmailSmtpVerificationMailSender(
        GmailSmtpProperties properties,
        SecretProviderRegistry secrets,
        GmailMailTransport transport
    ) {
        this.properties = properties;
        this.secrets = secrets;
        this.transport = transport;
    }

    @Override
    public void sendOtp(OtpMailMessage message) {
        char[] code = message.code().copy();
        try (SecretValue password = secrets.getSecret(properties.password())) {
            char[] passwordChars = password.copy();
            try {
                transport.send(
                    properties,
                    message.recipient(),
                    subject(message.purpose()),
                    body(code, message),
                    passwordChars
                );
            } finally {
                Arrays.fill(passwordChars, '\0');
            }
        } finally {
            Arrays.fill(code, '\0');
        }
    }

    private String subject(OtpMailPurpose purpose) {
        return switch (purpose) {
            case SIGNUP -> "[SSO Lab] 회원가입 인증 코드";
            case LOGIN -> "[SSO Lab] 로그인 인증 코드";
            case ADMIN_REAUTH -> "[SSO Lab] 관리자 재인증 코드";
        };
    }

    private String body(char[] code, OtpMailMessage message) {
        return "인증 코드는 " + new String(code) + " 입니다.\n"
            + "만료 시각: " + message.expiresAt() + "\n"
            + "본인이 요청하지 않았다면 이 메일을 무시해주세요.";
    }
}
