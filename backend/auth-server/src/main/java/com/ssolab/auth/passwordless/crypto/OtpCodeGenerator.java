package com.ssolab.auth.passwordless.crypto;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class OtpCodeGenerator {

    private static final int OTP_BOUND = 1_000_000;

    private final SecureRandom secureRandom;

    public OtpCodeGenerator() {
        this(new SecureRandom());
    }

    OtpCodeGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public SensitiveCode generate() {
        int value = secureRandom.nextInt(OTP_BOUND);
        char[] digits = new char[6];
        for (int index = digits.length - 1; index >= 0; index--) {
            digits[index] = (char) ('0' + value % 10);
            value /= 10;
        }
        try {
            return SensitiveCode.of(digits);
        } finally {
            java.util.Arrays.fill(digits, '\0');
        }
    }
}
