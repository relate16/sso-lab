package com.ssolab.auth.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IdentityInputNormalizerTest {

    private final IdentityInputNormalizer normalizer = new IdentityInputNormalizer();

    @Test
    void normalizesUserIdUsernameAndGroupName() {
        assertThat(normalizer.normalizeUserId("  User.Name-1 ")).isEqualTo("user.name-1");
        assertThat(normalizer.normalizeUsername("  홍길동  ")).isEqualTo("홍길동");
        assertThat(normalizer.normalizeGroupName("  개발본부  ")).isEqualTo("개발본부");
        assertThat(normalizer.groupUniquenessKey("Backend Team")).isEqualTo("backend team");
    }

    @Test
    void rejectsUnsafeUserIdCharacters() {
        assertThatThrownBy(() -> normalizer.normalizeUserId("bad user"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
