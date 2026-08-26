package com.ssolab.auth.identity.crypto;

import com.ssolab.auth.secret.SecretProviderRegistry;
import com.ssolab.auth.secret.SecretReference;
import com.ssolab.auth.secret.SecretValue;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class SecretKeyMaterialLoader {

    private static final int AES_256_KEY_BYTES = 32;
    private static final int MINIMUM_HMAC_KEY_BYTES = 32;

    private final SecretProviderRegistry providerRegistry;

    public SecretKeyMaterialLoader(SecretProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    public SecretKey loadAes256Key(SecretReference reference) {
        byte[] decoded = decodeBase64(reference);
        try {
            if (decoded.length != AES_256_KEY_BYTES) {
                throw new IdentityCryptoException("AES-256 key must contain exactly 32 bytes");
            }
            return new SecretKeySpec(decoded, "AES");
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }

    public SecretKey loadHmacSha256Key(SecretReference reference) {
        byte[] decoded = decodeBase64(reference);
        try {
            if (decoded.length < MINIMUM_HMAC_KEY_BYTES) {
                throw new IdentityCryptoException("HMAC-SHA256 key must contain at least 32 bytes");
            }
            return new SecretKeySpec(decoded, "HmacSHA256");
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }

    private byte[] decodeBase64(SecretReference reference) {
        try (SecretValue secretValue = providerRegistry.getSecret(reference)) {
            char[] characters = secretValue.copy();
            byte[] encoded = new byte[characters.length];
            try {
                for (int index = 0; index < characters.length; index++) {
                    if (characters[index] > 0x7f) {
                        throw new IdentityCryptoException("secret key must be Base64-encoded ASCII");
                    }
                    encoded[index] = (byte) characters[index];
                }
                return Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException exception) {
                throw new IdentityCryptoException("secret key is not valid Base64", exception);
            } finally {
                Arrays.fill(characters, '\0');
                Arrays.fill(encoded, (byte) 0);
            }
        }
    }
}
