package com.ssolab.auth.identity.crypto;

import com.ssolab.auth.secret.SecretProviderType;
import com.ssolab.auth.secret.SecretReference;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sso.identity.crypto")
public record IdentityCryptoProperties(
    EmailEncryption emailEncryption,
    SecretLocation emailLookupHmac
) {

    public IdentityCryptoProperties {
        if (emailEncryption == null) {
            throw new IllegalArgumentException("email encryption configuration is required");
        }
        if (emailLookupHmac == null) {
            throw new IllegalArgumentException("email lookup HMAC configuration is required");
        }
    }

    public record EmailEncryption(int currentKeyVersion, Map<Integer, SecretLocation> keys) {

        public EmailEncryption {
            if (currentKeyVersion <= 0) {
                throw new IllegalArgumentException("current email key version must be positive");
            }
            keys = keys == null ? Map.of() : Map.copyOf(keys);
            if (!keys.containsKey(currentKeyVersion)) {
                throw new IllegalArgumentException("current email encryption key version is not configured");
            }
        }

        public SecretReference secretReference(int keyVersion) {
            SecretLocation location = keys.get(keyVersion);
            if (location == null) {
                throw new IdentityCryptoException("email encryption key version is not configured: " + keyVersion);
            }
            return location.toReference();
        }
    }

    public record SecretLocation(SecretProviderType provider, String reference) {

        public SecretLocation {
            if (provider == null) {
                throw new IllegalArgumentException("secret provider is required");
            }
            if (reference == null || reference.isBlank()) {
                throw new IllegalArgumentException("secret reference is required");
            }
            reference = reference.trim();
        }

        public SecretReference toReference() {
            return new SecretReference(provider, reference);
        }
    }
}
