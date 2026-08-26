package com.ssolab.auth.secret;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DockerSecretProvider implements SecretProvider {

    private static final Pattern SAFE_REFERENCE = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final Path basePath;

    public DockerSecretProvider(DockerSecretProperties properties) {
        this.basePath = properties.basePath();
    }

    @Override
    public SecretProviderType type() {
        return SecretProviderType.DOCKER_SECRET;
    }

    @Override
    public SecretValue getSecret(String reference) {
        if (reference == null || !SAFE_REFERENCE.matcher(reference).matches()) {
            throw new SecretUnavailableException("invalid Docker secret reference");
        }

        Path secretPath = basePath.resolve(reference).normalize();
        if (!secretPath.getParent().equals(basePath)) {
            throw new SecretUnavailableException("Docker secret reference escapes the configured directory");
        }

        try {
            String value = Files.readString(secretPath, StandardCharsets.UTF_8).strip();
            return SecretValue.of(value);
        } catch (IOException exception) {
            throw new SecretUnavailableException("Docker secret is unavailable for reference: " + reference, exception);
        }
    }
}
