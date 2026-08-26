package com.ssolab.auth.passwordless.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SensitiveCodeTest {

    @Test
    void redactsAndDestroysCodeMaterial() {
        SensitiveCode code = SensitiveCode.of("123456".toCharArray());

        assertThat(code.toString()).doesNotContain("123456").contains("REDACTED");
        code.close();

        assertThatThrownBy(code::copy).isInstanceOf(IllegalStateException.class);
    }
}
