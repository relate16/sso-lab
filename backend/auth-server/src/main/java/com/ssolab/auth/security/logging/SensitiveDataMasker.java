package com.ssolab.auth.security.logging;

import java.util.regex.Pattern;

public final class SensitiveDataMasker {
    private static final Pattern LABELED_SECRET = Pattern.compile(
        "(?i)(otp|totp|recovery(?:Code)?|accessToken|refreshToken|idToken|"
            + "authorizationCode|clientSecret|serviceSecret|privateKey|aesKey|hmacKey|"
            + "reauthProof|turnstileToken|turnstileSecret)\\s*[:=]\\s*[^\\s,;]+"
    );
    private static final Pattern EMAIL = Pattern.compile(
        "(?i)[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
            + "(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+"
    );
    private static final Pattern JWT = Pattern.compile(
        "eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"
    );

    private SensitiveDataMasker() {
    }

    public static String mask(String value) {
        if (value == null) {
            return null;
        }
        String masked = LABELED_SECRET.matcher(value).replaceAll("credential=<redacted>");
        masked = EMAIL.matcher(masked).replaceAll("<redacted-email>");
        return JWT.matcher(masked).replaceAll("<redacted-jwt>");
    }
}
