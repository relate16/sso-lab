package com.ssolab.auth.secret;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sso.secrets.docker")
public record DockerSecretProperties(Path basePath) {

    public DockerSecretProperties {
        basePath = basePath == null ? Path.of("/run/secrets") : basePath.toAbsolutePath().normalize();
    }
}
