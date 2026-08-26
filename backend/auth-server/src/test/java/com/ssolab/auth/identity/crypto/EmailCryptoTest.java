package com.ssolab.auth.identity.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssolab.auth.secret.SecretProvider;
import com.ssolab.auth.secret.SecretProviderRegistry;
import com.ssolab.auth.secret.SecretProviderType;
import com.ssolab.auth.secret.SecretValue;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailCryptoTest {

    private EmailCipher emailCipher;
    private EmailLookupHasher lookupHasher;
    private EmailNormalizer emailNormalizer;

    @BeforeEach
    void setUp() {
        Map<String, String> secrets = Map.of(
            "email-key-v1", base64Key((byte) 0x21),
            "email-hmac-key", base64Key((byte) 0x63)
        );
        SecretProvider provider = new MapSecretProvider(secrets);
        SecretKeyMaterialLoader keyLoader = new SecretKeyMaterialLoader(
            new SecretProviderRegistry(List.of(provider))
        );
        IdentityCryptoProperties properties = new IdentityCryptoProperties(
            new IdentityCryptoProperties.EmailEncryption(
                1,
                Map.of(1, new IdentityCryptoProperties.SecretLocation(
                    SecretProviderType.ENVIRONMENT,
                    "email-key-v1"
                ))
            ),
            new IdentityCryptoProperties.SecretLocation(
                SecretProviderType.ENVIRONMENT,
                "email-hmac-key"
            )
        );
        emailCipher = new EmailCipher(properties, keyLoader);
        lookupHasher = new EmailLookupHasher(properties, keyLoader);
        emailNormalizer = new EmailNormalizer();
    }

    @Test
    void encryptsWithRandomIvAndDecryptsNormalizedEmail() {
        String normalizedEmail = emailNormalizer.normalize("  User@Example.COM ");

        EncryptedEmail first = emailCipher.encrypt(normalizedEmail);
        EncryptedEmail second = emailCipher.encrypt(normalizedEmail);

        assertThat(normalizedEmail).isEqualTo("user@example.com");
        assertThat(first.iv()).hasSize(12).isNotEqualTo(second.iv());
        assertThat(java.util.Arrays.equals(
            first.ciphertext(),
            normalizedEmail.getBytes(StandardCharsets.UTF_8)
        )).isFalse();
        assertThat(emailCipher.decrypt(first)).isEqualTo(normalizedEmail);
        assertThat(first.keyVersion()).isEqualTo(1);
    }

    @Test
    void createsDeterministicHmacForNormalizedLookupWithoutUsingCiphertext() {
        String normalizedEmail = emailNormalizer.normalize("User@Example.COM");

        byte[] first = lookupHasher.hash(normalizedEmail);
        byte[] second = lookupHasher.hash(normalizedEmail);
        byte[] other = lookupHasher.hash("other@example.com");

        assertThat(first).hasSize(32).isEqualTo(second).isNotEqualTo(other);
        assertThat(first).isNotEqualTo(emailCipher.encrypt(normalizedEmail).ciphertext());
    }

    private static String base64Key(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private record MapSecretProvider(Map<String, String> secrets) implements SecretProvider {

        @Override
        public SecretProviderType type() {
            return SecretProviderType.ENVIRONMENT;
        }

        @Override
        public SecretValue getSecret(String reference) {
            return SecretValue.of(secrets.get(reference));
        }
    }
}
