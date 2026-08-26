package com.ssolab.auth.identity.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EmailCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final IdentityCryptoProperties properties;
    private final SecretKeyMaterialLoader keyMaterialLoader;
    private final SecureRandom secureRandom;

    @Autowired
    public EmailCipher(
        IdentityCryptoProperties properties,
        SecretKeyMaterialLoader keyMaterialLoader
    ) {
        this(properties, keyMaterialLoader, new SecureRandom());
    }

    EmailCipher(
        IdentityCryptoProperties properties,
        SecretKeyMaterialLoader keyMaterialLoader,
        SecureRandom secureRandom
    ) {
        this.properties = properties;
        this.keyMaterialLoader = keyMaterialLoader;
        this.secureRandom = secureRandom;
    }

    public EncryptedEmail encrypt(String normalizedEmail) {
        int keyVersion = properties.emailEncryption().currentKeyVersion();
        SecretKey key = keyMaterialLoader.loadAes256Key(
            properties.emailEncryption().secretReference(keyVersion)
        );
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(keyVersion));
            byte[] ciphertext = cipher.doFinal(normalizedEmail.getBytes(StandardCharsets.UTF_8));
            return new EncryptedEmail(ciphertext, iv, keyVersion);
        } catch (GeneralSecurityException exception) {
            throw new IdentityCryptoException("email encryption failed", exception);
        }
    }

    public String decrypt(EncryptedEmail encryptedEmail) {
        SecretKey key = keyMaterialLoader.loadAes256Key(
            properties.emailEncryption().secretReference(encryptedEmail.keyVersion())
        );
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                new GCMParameterSpec(TAG_BITS, encryptedEmail.iv())
            );
            cipher.updateAAD(aad(encryptedEmail.keyVersion()));
            byte[] plaintext = cipher.doFinal(encryptedEmail.ciphertext());
            try {
                return new String(plaintext, StandardCharsets.UTF_8);
            } finally {
                java.util.Arrays.fill(plaintext, (byte) 0);
            }
        } catch (GeneralSecurityException exception) {
            throw new IdentityCryptoException("email decryption failed", exception);
        }
    }

    private byte[] aad(int keyVersion) {
        return ("sso-lab:email:v" + keyVersion).getBytes(StandardCharsets.US_ASCII);
    }
}
