package com.ssolab.auth.identity.crypto;

public record EncryptedEmail(byte[] ciphertext, byte[] iv, int keyVersion) {

    public EncryptedEmail {
        if (ciphertext == null || ciphertext.length == 0) {
            throw new IllegalArgumentException("ciphertext must not be empty");
        }
        if (iv == null || iv.length != 12) {
            throw new IllegalArgumentException("AES-GCM IV must contain 12 bytes");
        }
        if (keyVersion <= 0) {
            throw new IllegalArgumentException("key version must be positive");
        }
        ciphertext = ciphertext.clone();
        iv = iv.clone();
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    @Override
    public byte[] iv() {
        return iv.clone();
    }
}
