package com.ssolab.auth.security.ratelimit;

import com.ssolab.auth.identity.crypto.EmailLookupHasher;
import com.ssolab.auth.identity.crypto.EmailNormalizer;
import com.ssolab.auth.identity.service.IdentityInputNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class SecurityRequestIdentifiers {
    private final IdentityInputNormalizer identityNormalizer;
    private final EmailNormalizer emailNormalizer;
    private final EmailLookupHasher emailLookupHasher;

    public SecurityRequestIdentifiers(
        IdentityInputNormalizer identityNormalizer,
        EmailNormalizer emailNormalizer,
        EmailLookupHasher emailLookupHasher
    ) {
        this.identityNormalizer = identityNormalizer;
        this.emailNormalizer = emailNormalizer;
        this.emailLookupHasher = emailLookupHasher;
    }

    public String remoteAddress(HttpServletRequest request) {
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    public String userId(String value) {
        try {
            return identityNormalizer.normalizeUserId(value);
        } catch (IllegalArgumentException exception) {
            return "invalid-user-id";
        }
    }

    public String signup(String userId, String email) {
        String normalizedUserId = userId(userId);
        try {
            byte[] lookupHash = emailLookupHasher.hash(emailNormalizer.normalize(email));
            return normalizedUserId + ':'
                + Base64.getUrlEncoder().withoutPadding().encodeToString(lookupHash);
        } catch (IllegalArgumentException exception) {
            return normalizedUserId + ":invalid-email";
        }
    }

    public String email(String email) {
        try {
            byte[] lookupHash = emailLookupHasher.hash(emailNormalizer.normalize(email));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(lookupHash);
        } catch (IllegalArgumentException exception) {
            return "invalid-email";
        }
    }
}
