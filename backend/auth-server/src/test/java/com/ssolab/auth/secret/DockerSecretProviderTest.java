package com.ssolab.auth.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DockerSecretProviderTest {

    @Test
    void readsOnlySafeReferencesAndNeverRendersTheValue() throws Exception {
        Path secretDirectory = Path.of("build", "tmp", "docker-secret-" + UUID.randomUUID());
        Files.createDirectories(secretDirectory);
        try {
            Files.writeString(secretDirectory.resolve("email-key"), "test-secret-value\n");
            DockerSecretProvider provider = new DockerSecretProvider(
                new DockerSecretProperties(secretDirectory)
            );

            try (SecretValue value = provider.getSecret("email-key")) {
                assertThat(value.copy()).containsExactly("test-secret-value".toCharArray());
                assertThat(value.toString()).isEqualTo("SecretValue[REDACTED]");
            }

            assertThatThrownBy(() -> provider.getSecret("../outside"))
                .isInstanceOf(SecretUnavailableException.class)
                .hasMessageContaining("invalid Docker secret reference");
        } finally {
            Files.deleteIfExists(secretDirectory.resolve("email-key"));
            Files.deleteIfExists(secretDirectory);
        }
    }
}
