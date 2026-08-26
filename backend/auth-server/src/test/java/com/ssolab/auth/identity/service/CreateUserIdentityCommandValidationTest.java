package com.ssolab.auth.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class CreateUserIdentityCommandValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsRequiredIdentityFields() {
        CreateUserIdentityCommand command = new CreateUserIdentityCommand(
            "user.name",
            "홍길동",
            "user@example.com"
        );

        assertThat(validator.validate(command)).isEmpty();
    }

    @Test
    void rejectsInvalidEmailAndOutOfRangeFields() {
        CreateUserIdentityCommand command = new CreateUserIdentityCommand(
            "abc",
            " ",
            "not-an-email"
        );

        assertThat(validator.validate(command))
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("userId", "username", "email");
    }
}
