package com.ssolab.auth.passwordless.crypto;

import com.ssolab.auth.secret.SecretProviderType;
import com.ssolab.auth.secret.SecretReference;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sso.passwordless.crypto")
public record PasswordlessCryptoProperties(
    SecretLocation otpVerifier,
    VersionedSecret totpEncryption
) {

    public PasswordlessCryptoProperties {
        if (otpVerifier == null || totpEncryption == null) {
            throw new IllegalArgumentException("passwordless crypto configuration is required");
        }
    }

    public record VersionedSecret(int currentKeyVersion, Map<Integer, SecretLocation> keys) {

        public VersionedSecret {
            if (currentKeyVersion <= 0) {
                throw new IllegalArgumentException("current TOTP key version must be positive");
            }
            keys = keys == null ? Map.of() : Map.copyOf(keys);
            if (!keys.containsKey(currentKeyVersion)) {
                throw new IllegalArgumentException("current TOTP encryption key version is not configured");
            }
        }

        public SecretReference secretReference(int version) {
            SecretLocation location = keys.get(version);
            if (location == null) {
                throw new PasswordlessCryptoException(
                    "TOTP encryption key version is not configured: " + version
                );
            }
            return location.toReference();
        }
    }

    public record SecretLocation(SecretProviderType provider, String reference) {

        public SecretLocation {
            if (provider == null || reference == null || reference.isBlank()) {
                throw new IllegalArgumentException("secret provider and reference are required");
            }
            reference = reference.trim();
        }

        public SecretReference toReference() {
            return new SecretReference(provider, reference);
        }
    }
}
