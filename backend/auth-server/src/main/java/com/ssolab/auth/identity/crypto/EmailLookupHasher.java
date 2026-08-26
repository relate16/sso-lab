package com.ssolab.auth.identity.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class EmailLookupHasher {

    private final IdentityCryptoProperties properties;
    private final SecretKeyMaterialLoader keyMaterialLoader;

    public EmailLookupHasher(
        IdentityCryptoProperties properties,
        SecretKeyMaterialLoader keyMaterialLoader
    ) {
        this.properties = properties;
        this.keyMaterialLoader = keyMaterialLoader;
    }

    public byte[] hash(String normalizedEmail) {
        SecretKey key = keyMaterialLoader.loadHmacSha256Key(
            properties.emailLookupHmac().toReference()
        );
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(normalizedEmail.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IdentityCryptoException("email lookup HMAC failed", exception);
        }
    }
}
