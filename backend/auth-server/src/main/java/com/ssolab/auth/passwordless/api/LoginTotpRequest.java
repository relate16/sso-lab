package com.ssolab.auth.passwordless.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginTotpRequest(
    @NotBlank @Size(max = 30) String userId,
    @NotBlank @Pattern(regexp = "[0-9]{6}") String code
) {

    public char[] copyCode() {
        return code.toCharArray();
    }

    @Override
    public String toString() {
        return "LoginTotpRequest[userId=REDACTED, code=REDACTED]";
    }
}
