package com.ssolab.auth.security.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest {
    @Test
    void masksCredentialsEmailsAndJwtShapes() {
        String jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.abcdefghijklmnop";
        String masked = SensitiveDataMasker.mask(
            "otp=123456 email=person@example.com accessToken=" + jwt);

        assertThat(masked)
            .doesNotContain("123456", "person@example.com", jwt)
            .contains("<redacted>", "<redacted-email>");
    }
}
