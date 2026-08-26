package com.ssolab.auth.passwordless.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SensitiveRequestRedactionTest {

    @Test
    void requestObjectsDoNotRenderAuthenticationSecrets() {
        assertThat(new SensitiveCodeRequest("123456").toString()).doesNotContain("123456");
        assertThat(new ChallengeCodeRequest(UUID.randomUUID(), "654321").toString())
            .doesNotContain("654321");
        assertThat(new LoginTotpRequest("person", "112233").toString())
            .doesNotContain("112233");
        assertThat(new RecoveryStartRequest("person", "RECOVERY-CODE").toString())
            .doesNotContain("RECOVERY-CODE");
        assertThat(new LoginOtpRequest("person", "turnstile-response").toString())
            .doesNotContain("person", "turnstile-response");
        assertThat(new SignupStartRequest(
            "person", "Person", "person@example.com", "turnstile-response").toString())
            .doesNotContain("person@example.com", "turnstile-response");
    }
}
