package com.ssolab.auth.passwordless.crypto;

import com.ssolab.auth.identity.crypto.SecretKeyMaterialLoader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TotpSecretCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final PasswordlessCryptoProperties properties;
    private final SecretKeyMaterialLoader keyMaterialLoader;
    private final SecureRandom secureRandom;

    @Autowired
    public TotpSecretCipher(
        PasswordlessCryptoProperties properties,
        SecretKeyMaterialLoader keyMaterialLoader
    ) {
        this(properties, keyMaterialLoader, new SecureRandom());
    }

    TotpSecretCipher(
        PasswordlessCryptoProperties properties,
        SecretKeyMaterialLoader keyMaterialLoader,
        SecureRandom secureRandom
    ) {
        this.properties = properties;
        this.keyMaterialLoader = keyMaterialLoader;
        this.secureRandom = secureRandom;
    }

    public EncryptedTotpSecret encrypt(UUID userId, byte[] plaintext) {
        int version = properties.totpEncryption().currentKeyVersion();
        SecretKey key = keyMaterialLoader.loadAes256Key(
            properties.totpEncryption().secretReference(version)
        );
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(userId, version));
            return new EncryptedTotpSecret(cipher.doFinal(plaintext), iv, version);
        } catch (GeneralSecurityException exception) {
            throw new PasswordlessCryptoException("TOTP secret encryption failed", exception);
        }
    }

    public byte[] decrypt(UUID userId, EncryptedTotpSecret encrypted) {
        SecretKey key = keyMaterialLoader.loadAes256Key(
            properties.totpEncryption().secretReference(encrypted.keyVersion())
        );
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                new GCMParameterSpec(TAG_BITS, encrypted.iv())
            );
            cipher.updateAAD(aad(userId, encrypted.keyVersion()));
            return cipher.doFinal(encrypted.ciphertext());
        } catch (GeneralSecurityException exception) {
            throw new PasswordlessCryptoException("TOTP secret decryption failed", exception);
        }
    }

    private byte[] aad(UUID userId, int version) {
        return ("sso-lab:totp:" + userId + ":v" + version)
            .getBytes(StandardCharsets.US_ASCII);
    }
}
