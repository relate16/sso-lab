package com.ssolab.auth.passwordless.crypto;

import com.ssolab.auth.identity.crypto.SecretKeyMaterialLoader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class OtpVerifier {

    private final PasswordlessCryptoProperties properties;
    private final SecretKeyMaterialLoader keyMaterialLoader;

    public OtpVerifier(
        PasswordlessCryptoProperties properties,
        SecretKeyMaterialLoader keyMaterialLoader
    ) {
        this.properties = properties;
        this.keyMaterialLoader = keyMaterialLoader;
    }

    public byte[] hash(UUID challengeId, String purpose, char[] code) {
        if (!isSixDigits(code)) {
            return new byte[32];
        }
        SecretKey key = keyMaterialLoader.loadHmacSha256Key(properties.otpVerifier().toReference());
        byte[] message = message(challengeId, purpose, code);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(message);
        } catch (GeneralSecurityException exception) {
            throw new PasswordlessCryptoException("OTP verifier creation failed", exception);
        } finally {
            Arrays.fill(message, (byte) 0);
        }
    }

    private boolean isSixDigits(char[] code) {
        if (code == null || code.length != 6) {
            return false;
        }
        for (char digit : code) {
            if (digit < '0' || digit > '9') {
                return false;
            }
        }
        return true;
    }

    public boolean matches(byte[] expected, UUID challengeId, String purpose, char[] candidate) {
        byte[] actual = hash(challengeId, purpose, candidate);
        try {
            return MessageDigest.isEqual(expected, actual);
        } finally {
            Arrays.fill(actual, (byte) 0);
        }
    }

    private byte[] message(UUID challengeId, String purpose, char[] code) {
        byte[] prefix = (challengeId + ":" + purpose + ":").getBytes(StandardCharsets.US_ASCII);
        ByteBuffer encoded = StandardCharsets.US_ASCII.encode(CharBuffer.wrap(code));
        byte[] result = new byte[prefix.length + encoded.remaining()];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        encoded.get(result, prefix.length, encoded.remaining());
        return result;
    }
}
