package com.ssolab.auth.passwordless.mail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class InMemoryVerificationMailSender implements VerificationMailSender {

    private final List<CapturedOtpMail> messages = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void sendOtp(OtpMailMessage message) {
        char[] code = message.code().copy();
        try {
            messages.add(new CapturedOtpMail(
                message.challengeId(),
                message.purpose(),
                new String(code),
                message.expiresAt()
            ));
        } finally {
            java.util.Arrays.fill(code, '\0');
        }
    }

    public List<CapturedOtpMail> messages() {
        synchronized (messages) {
            return List.copyOf(messages);
        }
    }

    public void clear() {
        messages.clear();
    }
}
