package com.ssolab.auth.oidc.crypto;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
import com.ssolab.auth.oidc.config.OidcProperties;
import com.ssolab.auth.secret.SecretProviderRegistry;
import com.ssolab.auth.secret.SecretValue;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class OidcSigningKeyLoader {

    private final SecretProviderRegistry secretProviderRegistry;
    private final OidcProperties properties;

    public OidcSigningKeyLoader(
        SecretProviderRegistry secretProviderRegistry,
        OidcProperties properties
    ) {
        this.secretProviderRegistry = secretProviderRegistry;
        this.properties = properties;
    }

    public RSAKey load() {
        OidcProperties.Signing signing = properties.getSigning();
        byte[] privateDer = decode(signing.privateKeySecret());
        byte[] publicDer = decode(signing.publicKeySecret());
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(
                new PKCS8EncodedKeySpec(privateDer)
            );
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                new X509EncodedKeySpec(publicDer)
            );
            if (publicKey.getModulus().bitLength() < 2048
                || !publicKey.getModulus().equals(privateKey.getModulus())) {
                throw new IllegalStateException("OIDC RSA signing key pair is invalid");
            }
            return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .algorithm(JWSAlgorithm.RS256)
                .keyID(signing.getKeyId())
                .build();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("OIDC signing key could not be loaded", exception);
        } finally {
            Arrays.fill(privateDer, (byte) 0);
            Arrays.fill(publicDer, (byte) 0);
        }
    }

    private byte[] decode(com.ssolab.auth.secret.SecretReference reference) {
        try (SecretValue value = secretProviderRegistry.getSecret(reference)) {
            char[] encoded = value.copy();
            byte[] ascii = new byte[encoded.length];
            try {
                for (int index = 0; index < encoded.length; index++) {
                    if (encoded[index] > 127) {
                        throw new IllegalStateException("OIDC key secret must be Base64 ASCII");
                    }
                    ascii[index] = (byte) encoded[index];
                }
                return Base64.getDecoder().decode(ascii);
            } finally {
                Arrays.fill(encoded, '\0');
                Arrays.fill(ascii, (byte) 0);
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("OIDC key secret is not valid Base64", exception);
        }
    }
}
