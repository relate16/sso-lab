package com.ssolab.auth.identity.api;

import com.ssolab.auth.oidc.logout.SessionView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public final class UserProfileDtos {
    private UserProfileDtos() {
    }

    public record ProfileResponse(
        String userId,
        String username,
        String email,
        List<String> roles,
        List<String> groups,
        boolean totpEnrolled,
        List<SessionView> sessions
    ) {
        public ProfileResponse {
            roles = List.copyOf(roles);
            groups = List.copyOf(groups);
            sessions = List.copyOf(sessions);
        }

        @Override
        public String toString() {
            return "ProfileResponse[userId=<redacted>, username=<redacted>, email=<redacted>, "
                + "roles=" + roles + ", groups=" + groups.size()
                + ", totpEnrolled=" + totpEnrolled + ", sessions=" + sessions.size() + "]";
        }
    }

    public record UsernameChangeRequest(
        @NotBlank @Size(max = 100) String username
    ) {
        @Override
        public String toString() {
            return "UsernameChangeRequest[username=<redacted>]";
        }
    }

    public record EmailChangeStartRequest(
        @NotBlank @Email @Size(max = 320) String newEmail
    ) {
        @Override
        public String toString() {
            return "EmailChangeStartRequest[newEmail=<redacted>]";
        }
    }

    public record EmailChangeVerifyRequest(
        @NotNull UUID challengeId,
        @jakarta.validation.constraints.Pattern(regexp = "[0-9]{6}") String code
    ) {
        public char[] copyCode() {
            return code == null ? new char[0] : code.toCharArray();
        }

        @Override
        public String toString() {
            return "EmailChangeVerifyRequest[challengeId=" + challengeId + ", code=<redacted>]";
        }
    }

    public record SelfReauthVerifyRequest(
        @NotBlank String method,
        UUID challengeId,
        @jakarta.validation.constraints.Pattern(regexp = "[0-9]{6}") String code
    ) {
        public char[] copyCode() {
            return code == null ? new char[0] : code.toCharArray();
        }

        @Override
        public String toString() {
            return "SelfReauthVerifyRequest[method=" + method
                + ", challengeId=" + challengeId + ", code=<redacted>]";
        }
    }

    public record SelfReauthResponse(boolean reauthenticated, Instant expiresAt) {
    }

    public record AccountDeleteRequest(
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "DELETE") String confirmation
    ) {
        @Override
        public String toString() {
            return "AccountDeleteRequest[confirmation=<redacted>]";
        }
    }
}
