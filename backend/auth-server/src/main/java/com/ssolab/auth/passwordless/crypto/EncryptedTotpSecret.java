package com.ssolab.auth.passwordless.crypto;

public record EncryptedTotpSecret(byte[] ciphertext, byte[] iv, int keyVersion) {

    public EncryptedTotpSecret {
        ciphertext = ciphertext.clone();
        iv = iv.clone();
        if (iv.length != 12 || keyVersion <= 0) {
            throw new IllegalArgumentException("invalid encrypted TOTP secret metadata");
        }
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
