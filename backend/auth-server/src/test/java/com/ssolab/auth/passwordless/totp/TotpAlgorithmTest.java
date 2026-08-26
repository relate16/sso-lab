package com.ssolab.auth.passwordless.totp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpAlgorithmTest {

    private final TotpAlgorithm algorithm = new TotpAlgorithm();
    private final byte[] rfcSecret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesRfc6238Sha1VectorUsingSixDigits() {
        assertThat(algorithm.generateCode(rfcSecret, Instant.ofEpochSecond(59)))
            .isEqualTo("287082");
    }

    @Test
    void acceptsOnlyCurrentPlusOrMinusOneTimeStep() {
        Instant now = Instant.ofEpochSecond(1_111_111_111L);
        String previous = algorithm.generateCode(rfcSecret, now.minusSeconds(30));
        String next = algorithm.generateCode(rfcSecret, now.plusSeconds(30));
        String outside = algorithm.generateCode(rfcSecret, now.plusSeconds(60));

        assertThat(algorithm.matchingCounter(rfcSecret, now, previous.toCharArray())).isPresent();
        assertThat(algorithm.matchingCounter(rfcSecret, now, next.toCharArray())).isPresent();
        assertThat(algorithm.matchingCounter(rfcSecret, now, outside.toCharArray())).isEmpty();
    }
}
