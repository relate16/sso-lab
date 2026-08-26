package com.ssolab.auth.admin.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {
    }

    public record UserView(
        UUID id,
        String userId,
        String username,
        String maskedEmail,
        String status,
        List<String> roles,
        List<GroupMembership> groups,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record GroupMembership(UUID id, String name, String fullPath) {
    }

    public record GroupView(
        UUID id,
        String name,
        UUID parentId,
        String fullPath,
        long memberCount,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record AuditView(
        UUID id,
        String event,
        UUID actorId,
        UUID targetId,
        boolean success,
        String source,
        String traceId,
        Instant occurredAt
    ) {
    }

    public record RolesRequest(@NotEmpty Set<@NotBlank String> roles) {
    }

    public record GroupsRequest(@NotNull Set<@NotNull UUID> groupIds) {
    }

    public record CreateGroupRequest(
        @NotBlank @Size(max = 100) String name,
        UUID parentId
    ) {
    }

    public record RenameGroupRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record MoveGroupRequest(UUID parentId) {
    }

    public record ReauthVerifyRequest(
        @NotBlank String method,
        UUID challengeId,
        @NotBlank @Size(min = 6, max = 10) String code
    ) {
    }

    public record ReauthStartResponse(UUID challengeId, boolean totpAvailable) {
    }

    public record ReauthProofResponse(String proof, Instant expiresAt) {
        @Override
        public String toString() {
            return "ReauthProofResponse[proof=<redacted>, expiresAt=" + expiresAt + "]";
        }
    }

    public record EmailRevealRequest(@NotBlank String proof) {
        @Override
        public String toString() {
            return "EmailRevealRequest[proof=<redacted>]";
        }
    }

    public record EmailRevealResponse(String email) {
        @Override
        public String toString() {
            return "EmailRevealResponse[email=<redacted>]";
        }
    }
}
