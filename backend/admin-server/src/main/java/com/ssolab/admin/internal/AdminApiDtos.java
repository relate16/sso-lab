package com.ssolab.admin.internal;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AdminApiDtos {
    private AdminApiDtos() {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {
    }
    public record UserView(UUID id, String userId, String username, String maskedEmail,
        String status, List<String> roles, List<GroupMembership> groups,
        Instant createdAt, Instant updatedAt) {
    }
    public record GroupMembership(UUID id, String name, String fullPath) {
    }
    public record GroupView(UUID id, String name, UUID parentId, String fullPath,
        long memberCount, Instant createdAt, Instant updatedAt) {
    }
    public record AuditView(UUID id, String event, UUID actorId, UUID targetId,
        boolean success, String source, String traceId, Instant occurredAt) {
    }
    public record RolesRequest(Set<String> roles) {
    }
    public record GroupsRequest(Set<UUID> groupIds) {
    }
    public record CreateGroupRequest(String name, UUID parentId) {
    }
    public record RenameGroupRequest(String name) {
    }
    public record MoveGroupRequest(UUID parentId) {
    }
    public record CreatedId(UUID id) {
    }
    public record ReauthStartResponse(UUID challengeId, boolean totpAvailable) {
    }
    public record ReauthVerifyRequest(String method, UUID challengeId, String code) {
        @Override public String toString() {
            return "ReauthVerifyRequest[method=" + method + ", challengeId=" + challengeId
                + ", code=<redacted>]";
        }
    }
    public record InternalProofResponse(String proof, Instant expiresAt) {
        @Override public String toString() {
            return "InternalProofResponse[proof=<redacted>, expiresAt=" + expiresAt + "]";
        }
    }
    public record EmailRevealRequest(String proof) {
        @Override public String toString() { return "EmailRevealRequest[proof=<redacted>]"; }
    }
    public record EmailRevealResponse(String email) {
        @Override public String toString() { return "EmailRevealResponse[email=<redacted>]"; }
    }
}
