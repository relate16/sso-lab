package com.ssolab.auth.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssolab.auth.passwordless.crypto.SensitiveCode;
import com.ssolab.auth.passwordless.mail.InMemoryVerificationMailSender;
import com.ssolab.auth.passwordless.mail.OtpMailMessage;
import com.ssolab.auth.passwordless.mail.OtpMailPurpose;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TestSupportControllerTest {

    private static final String KEY = "isolated-test-key";

    @Test
    void exposesCapturedOtpOnlyWithTestKeyAndUsesNoStore() {
        InMemoryVerificationMailSender mail = new InMemoryVerificationMailSender();
        AdjustableTestClock clock = new AdjustableTestClock();
        TestSupportController controller = new TestSupportController(
            new TestSupportProperties(KEY), mail, clock
        );
        UUID challengeId = UUID.randomUUID();
        try (SensitiveCode code = SensitiveCode.of("000000".toCharArray())) {
            mail.sendOtp(new OtpMailMessage(
                challengeId, OtpMailPurpose.LOGIN, "user@example.test", code,
                Instant.parse("2026-08-31T00:05:00Z")
            ));
        }

        assertThatThrownBy(() -> controller.mail(null, challengeId, OtpMailPurpose.LOGIN))
            .isInstanceOf(ResponseStatusException.class);
        var response = controller.mail(KEY, challengeId, OtpMailPurpose.LOGIN);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().code()).isEqualTo("000000");
    }

    @Test
    void controlsOnlyTheTestClock() {
        AdjustableTestClock clock = new AdjustableTestClock();
        TestSupportController controller = new TestSupportController(
            new TestSupportProperties(KEY), new InMemoryVerificationMailSender(), clock
        );
        Instant fixed = Instant.parse("2026-08-31T00:00:00Z");
        controller.setClock(KEY, new TestSupportController.ClockRequest(fixed));
        assertThat(clock.instant()).isEqualTo(fixed);
    }

    @Test
    void rejectsAnUnsafeTestSupportKeyWhenTheControllerIsCreated() {
        assertThatThrownBy(() -> new TestSupportController(
            new TestSupportProperties("too-short"),
            new InMemoryVerificationMailSender(),
            new AdjustableTestClock()
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least 16 characters");
    }
}
