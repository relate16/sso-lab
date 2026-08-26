package com.ssolab.auth.identity.crypto;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class EmailNormalizer {

    public String normalize(String email) {
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        String normalized = Normalizer.normalize(email.trim(), Normalizer.Form.NFC)
            .toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return normalized;
    }
}
