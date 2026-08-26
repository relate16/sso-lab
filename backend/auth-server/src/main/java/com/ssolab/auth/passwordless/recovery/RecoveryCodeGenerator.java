package com.ssolab.auth.passwordless.recovery;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class RecoveryCodeGenerator {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final int RAW_LENGTH = 26;

    private final SecureRandom secureRandom;

    public RecoveryCodeGenerator() {
        this(new SecureRandom());
    }

    RecoveryCodeGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generate() {
        StringBuilder value = new StringBuilder(RAW_LENGTH + 5);
        for (int index = 0; index < RAW_LENGTH; index++) {
            if (index > 0 && index % 5 == 0) {
                value.append('-');
            }
            value.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return value.toString();
    }
}
