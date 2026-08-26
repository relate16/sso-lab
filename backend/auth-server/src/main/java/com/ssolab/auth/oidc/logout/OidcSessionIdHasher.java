package com.ssolab.auth.oidc.logout;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class OidcSessionIdHasher {
    private OidcSessionIdHasher() {
    }

    public static String hash(String sessionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(sessionId.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
