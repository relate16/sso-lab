package com.ssolab.auth;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;

final class OidcTestProperties {

    static final String HR_SECRET = "hr-oidc-test-secret-only";
    static final String APPROVAL_SECRET = "approval-oidc-test-secret-only";
    static final String ADMIN_SECRET = "admin-oidc-test-secret-only";
    static final String INTERNAL_ADMIN_SECRET = "internal-admin-test-secret-only";
    private static final KeyPair KEY_PAIR = keyPair();

    private OidcTestProperties() {
    }

    static void register(DynamicPropertyRegistry registry) {
        registry.add("SSO_JWT_PRIVATE_KEY", () -> Base64.getEncoder()
            .encodeToString(KEY_PAIR.getPrivate().getEncoded()));
        registry.add("SSO_JWT_PUBLIC_KEY", () -> Base64.getEncoder()
            .encodeToString(KEY_PAIR.getPublic().getEncoded()));
        registry.add("HR_CLIENT_SECRET", () -> HR_SECRET);
        registry.add("APPROVAL_CLIENT_SECRET", () -> APPROVAL_SECRET);
        registry.add("ADMIN_CLIENT_SECRET", () -> ADMIN_SECRET);
        registry.add("ADMIN_INTERNAL_API_SECRET", () -> INTERNAL_ADMIN_SECRET);
        registry.add("AUTH_PUBLIC_URL", () -> "http://localhost:8080");
        registry.add("HR_REDIRECT_URI",
            () -> "http://localhost:8081/login/oauth2/code/hr-client");
        registry.add("APPROVAL_REDIRECT_URI",
            () -> "http://localhost:8082/login/oauth2/code/approval-client");
        registry.add("ADMIN_REDIRECT_URI",
            () -> "http://localhost:8083/login/oauth2/code/admin-client");
        registry.add("HR_POST_LOGOUT_REDIRECT_URI", () -> "http://localhost:8081/");
        registry.add("APPROVAL_POST_LOGOUT_REDIRECT_URI", () -> "http://localhost:8082/");
        registry.add("ADMIN_POST_LOGOUT_REDIRECT_URI", () -> "http://localhost:8083/");
        registry.add("HR_BACKCHANNEL_LOGOUT_URI", () -> "http://127.0.0.1:1/hr");
        registry.add("APPROVAL_BACKCHANNEL_LOGOUT_URI", () -> "http://127.0.0.1:1/approval");
        registry.add("ADMIN_BACKCHANNEL_LOGOUT_URI", () -> "http://127.0.0.1:1/admin");
    }

    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("test RSA key generation failed", exception);
        }
    }
}
