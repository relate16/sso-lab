package com.ssolab.auth.passwordless.api;

import com.ssolab.auth.identity.service.CreateUserIdentityCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupStartRequest(
    @NotBlank @Size(min = 4, max = 30) String userId,
    @NotBlank @Size(max = 100) String username,
    @NotBlank @Email @Size(max = 320) String email,
    @Size(max = 2048) String turnstileToken
) {

    public SignupStartRequest(String userId, String username, String email) {
        this(userId, username, email, null);
    }

    public CreateUserIdentityCommand toCommand() {
        return new CreateUserIdentityCommand(userId, username, email);
    }

    @Override
    public String toString() {
        return "SignupStartRequest[userId=REDACTED, username=REDACTED, email=REDACTED, "
            + "turnstileToken=REDACTED]";
    }
}
