package com.ssolab.auth.identity.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class IdentityInputNormalizer {

    private static final Pattern USER_ID_PATTERN = Pattern.compile("[a-z0-9._-]{4,30}");

    public String normalizeUserId(String userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        String normalized = userId.trim().toLowerCase(Locale.ROOT);
        if (!USER_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                "userId must be 4-30 characters using a-z, 0-9, dot, underscore, or hyphen"
            );
        }
        return normalized;
    }

    public String normalizeUsername(String username) {
        if (username == null) {
            throw new IllegalArgumentException("username must not be null");
        }
        String normalized = Normalizer.normalize(username.trim(), Normalizer.Form.NFC);
        if (normalized.isBlank() || normalized.length() > 100) {
            throw new IllegalArgumentException("username must contain 1-100 characters after trimming");
        }
        return normalized;
    }

    public String normalizeGroupName(String groupName) {
        if (groupName == null) {
            throw new IllegalArgumentException("group name must not be null");
        }
        String normalized = Normalizer.normalize(groupName.trim(), Normalizer.Form.NFC);
        if (normalized.isBlank() || normalized.length() > 100) {
            throw new IllegalArgumentException("group name must contain 1-100 characters after trimming");
        }
        return normalized;
    }

    public String groupUniquenessKey(String normalizedGroupName) {
        return normalizedGroupName.toLowerCase(Locale.ROOT);
    }
}
