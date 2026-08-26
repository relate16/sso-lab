package com.ssolab.auth.identity.service;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserIdentityCommand(
    @NotBlank @Size(min = 4, max = 30) String userId,
    @NotBlank @Size(max = 100) String username,
    @NotBlank @Email @Size(max = 320) String email
) {

    public CreateUserIdentityCommand {
        userId = userId == null ? null : userId.trim();
        username = username == null ? null : username.trim();
        email = email == null ? null : email.trim();
    }
}
