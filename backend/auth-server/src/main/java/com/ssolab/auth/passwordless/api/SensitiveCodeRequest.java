package com.ssolab.auth.passwordless.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SensitiveCodeRequest(
    @NotBlank @Pattern(regexp = "[0-9]{6}") String code
) {

    public char[] copyCode() {
        return code.toCharArray();
    }

    @Override
    public String toString() {
        return "SensitiveCodeRequest[code=REDACTED]";
    }
}
