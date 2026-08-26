package com.ssolab.auth.passwordless.totp;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class TotpAlgorithm {

    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;
    public static final int VALIDATION_WINDOW = 1;
    private static final int SECRET_BYTES = 20;

    private final SecureRandom secureRandom;

    public TotpAlgorithm() {
        this(new SecureRandom());
    }

    TotpAlgorithm(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public byte[] generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);
        return secret;
    }

    public String otpauthUri(String userId, byte[] secret) {
        String issuer = url("SSO Lab");
        String label = issuer + ":" + url(userId);
        return "otpauth://totp/" + label
            + "?secret=" + Base32.encode(secret)
            + "&issuer=" + issuer
            + "&algorithm=SHA1&digits=6&period=30";
    }

    public String generateCode(byte[] secret, Instant instant) {
        return codeForCounter(secret, instant.getEpochSecond() / PERIOD_SECONDS);
    }

    public OptionalLong matchingCounter(byte[] secret, Instant instant, char[] candidate) {
        if (candidate == null || candidate.length != DIGITS) {
            return OptionalLong.empty();
        }
        String candidateString = new String(candidate);
        if (!candidateString.matches("[0-9]{6}")) {
            return OptionalLong.empty();
        }
        long current = instant.getEpochSecond() / PERIOD_SECONDS;
        for (int offset = -VALIDATION_WINDOW; offset <= VALIDATION_WINDOW; offset++) {
            String expected = codeForCounter(secret, current + offset);
            if (constantTimeEquals(expected, candidateString)) {
                return OptionalLong.of(current + offset);
            }
        }
        return OptionalLong.empty();
    }

    private String codeForCounter(byte[] secret, long counter) {
        byte[] counterBytes = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
        byte[] digest = null;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            digest = mac.doFinal(counterBytes);
            int offset = digest[digest.length - 1] & 0x0f;
            int binary = ((digest[offset] & 0x7f) << 24)
                | ((digest[offset + 1] & 0xff) << 16)
                | ((digest[offset + 2] & 0xff) << 8)
                | (digest[offset + 3] & 0xff);
            return String.format(java.util.Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("RFC 6238 HMAC-SHA1 is unavailable", exception);
        } finally {
            Arrays.fill(counterBytes, (byte) 0);
            if (digest != null) {
                Arrays.fill(digest, (byte) 0);
            }
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.US_ASCII),
            actual.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
