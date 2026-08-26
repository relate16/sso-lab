package com.ssolab.hr.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OidcBffPropertiesTest {

    @TempDir
    Path tempDir;

    @Test
    void readsClientSecretFromMountedSecretFile() throws Exception {
        Path secretFile = tempDir.resolve("HR_CLIENT_SECRET");
        Files.writeString(secretFile, "file-only-secret\n");

        OidcBffProperties properties = new OidcBffProperties(
            "hr", "hr-client", null, secretFile.toString(),
            URI.create("https://auth.example.test"),
            URI.create("https://auth.example.test/oauth2/authorize"),
            URI.create("http://auth-server:8080/oauth2/token"),
            URI.create("http://auth-server:8080/oauth2/jwks"),
            URI.create("http://auth-server:8080/userinfo"),
            "https://hr.example.test/login/oauth2/code/hr",
            URI.create("https://hr.example.test/")
        );

        assertThat(properties.clientSecret()).isEqualTo("file-only-secret");
    }
}
