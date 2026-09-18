package com.ssolab.admin.api;

import com.ssolab.admin.internal.AdminApiDtos;
import com.ssolab.admin.internal.InternalAdminClient;
import com.ssolab.admin.session.ElevatedAdminSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminApiController {

    private final InternalAdminClient internalClient;
    private final ElevatedAdminSessionService elevatedSession;

    public AdminApiController(
        InternalAdminClient internalClient,
        ElevatedAdminSessionService elevatedSession
    ) {
        this.internalClient = internalClient;
        this.elevatedSession = elevatedSession;
    }

    @GetMapping("/dashboard")
    AdminApiDtos.DashboardView dashboard(@AuthenticationPrincipal OidcUser user) {
        return internalClient.dashboard(actor(user));
    }

    @GetMapping("/users")
    AdminApiDtos.PageResponse<AdminApiDtos.UserView> users(
        @AuthenticationPrincipal OidcUser user,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String role,
        @RequestParam(required = false) UUID groupId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size,
        @RequestParam(defaultValue = "normalizedUserId") String sort,
        @RequestParam(defaultValue = "asc") String direction
    ) {
        return internalClient.users(
            actor(user), q, status, role, groupId, page, size, sort, direction
        );
    }

    @GetMapping("/users/{userId}")
    AdminApiDtos.UserView user(
        @AuthenticationPrincipal OidcUser user,
        @PathVariable UUID userId
    ) {
        return internalClient.user(actor(user), userId);
    }

    @PostMapping("/users/{userId}/suspend")
    void suspend(@AuthenticationPrincipal OidcUser user, @PathVariable UUID userId) {
        internalClient.suspend(actor(user), userId, traceId());
    }

    @PostMapping("/users/{userId}/resume")
    void resume(@AuthenticationPrincipal OidcUser user, @PathVariable UUID userId) {
        internalClient.resume(actor(user), userId, traceId());
    }

    @PutMapping("/users/{userId}/roles")
    void roles(
        @AuthenticationPrincipal OidcUser user,
        @PathVariable UUID userId,
        @Valid @RequestBody RolesRequest request
    ) {
        internalClient.roles(
            actor(user), userId, new AdminApiDtos.RolesRequest(request.roles()), traceId()
        );
    }

    @PutMapping("/users/{userId}/groups")
    void groups(
        @AuthenticationPrincipal OidcUser user,
        @PathVariable UUID userId,
        @Valid @RequestBody GroupsRequest request
    ) {
        internalClient.groups(
            actor(user), userId, new AdminApiDtos.GroupsRequest(request.groupIds()), traceId()
        );
    }

    @PostMapping("/users/bulk/status")
    AdminApiDtos.BulkResult bulkStatus(
        @AuthenticationPrincipal OidcUser user,
        @Valid @RequestBody BulkStatusRequest request
    ) {
        return internalClient.bulkStatus(
            actor(user), new AdminApiDtos.BulkStatusRequest(
                request.userIds(), request.status()
            ), traceId()
        );
    }

    @PutMapping("/users/bulk/roles")
    AdminApiDtos.BulkResult bulkRoles(
        @AuthenticationPrincipal OidcUser user,
        @Valid @RequestBody BulkRolesRequest request
    ) {
        return internalClient.bulkRoles(
            actor(user), new AdminApiDtos.BulkRolesRequest(
                request.userIds(), request.roles()
            ), traceId()
        );
    }

    @PostMapping("/users/{userId}/email/reveal")
    AdminApiDtos.EmailRevealResponse revealEmail(
        @AuthenticationPrincipal OidcUser user,
        @PathVariable UUID userId,
        HttpSession session
    ) {
        UUID actorId = actor(user);
        String proof = elevatedSession.requireProof(session, actorId);
        return internalClient.revealEmail(actorId, userId, proof, traceId());
    }

    @GetMapping("/groups")
    List<AdminApiDtos.GroupView> groups(@AuthenticationPrincipal OidcUser user) {
        return internalClient.groups(actor(user));
    }

    @GetMapping("/groups/{groupId}")
    AdminApiDtos.GroupDetailView group(
        @AuthenticationPrincipal OidcUser user,
        @PathVariable UUID groupId
    ) {
        return internalClient.group(actor(user), groupId);
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    AdminApiDtos.CreatedId createGroup(
        @AuthenticationPrincipal OidcUser user,
        @Valid @RequestBody CreateGroupRequest request
    ) {
        return internalClient.createGroup(
            actor(user),
            new AdminApiDtos.CreateGroupRequest(request.name(), request.parentId()),
            traceId()
        );
    }

    @PatchMapping("/groups/{groupId}")
    void renameGroup(
        @AuthenticationPrincipal OidcUser user,
        @PathVariable UUID groupId,
        @Valid @RequestBody RenameGroupRequest request
    ) {
        internalClient.renameGroup(
            actor(user), groupId, new AdminApiDtos.RenameGroupRequest(request.name()), traceId()
        );
    }

    @PostMapping("/groups/{groupId}/move")
    void moveGroup(
        @AuthenticationPrincipal OidcUser user,
        @PathVariable UUID groupId,
        @RequestBody MoveGroupRequest request
    ) {
        internalClient.moveGroup(
            actor(user), groupId, new AdminApiDtos.MoveGroupRequest(request.parentId()), traceId()
        );
    }

    @DeleteMapping("/groups/{groupId}")
    void deleteGroup(@AuthenticationPrincipal OidcUser user, @PathVariable UUID groupId) {
        internalClient.deleteGroup(actor(user), groupId, traceId());
    }

    @PostMapping("/groups/{groupId}/members/{userId}")
    void addGroupMember(
        @AuthenticationPrincipal OidcUser user,
        @PathVariable UUID groupId,
        @PathVariable UUID userId
    ) {
        internalClient.addGroupMember(actor(user), groupId, userId, traceId());
    }

    @DeleteMapping("/groups/{groupId}/members/{userId}")
    void removeGroupMember(
        @AuthenticationPrincipal OidcUser user,
        @PathVariable UUID groupId,
        @PathVariable UUID userId
    ) {
        internalClient.removeGroupMember(actor(user), groupId, userId, traceId());
    }

    @GetMapping("/audit-logs")
    AdminApiDtos.PageResponse<AdminApiDtos.AuditView> audit(
        @AuthenticationPrincipal OidcUser user,
        @RequestParam(required = false) String event,
        @RequestParam(required = false) Boolean success,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(defaultValue = "occurredAt") String sort,
        @RequestParam(defaultValue = "desc") String direction
    ) {
        return internalClient.audit(
            actor(user), event, success, from, to, page, size, sort, direction
        );
    }

    @PostMapping("/reauth/email/start")
    AdminApiDtos.ReauthStartResponse startReauth(@AuthenticationPrincipal OidcUser user) {
        return internalClient.startReauth(actor(user));
    }

    @PostMapping("/reauth/verify")
    ElevatedAdminSessionService.ElevatedStatus verifyReauth(
        @AuthenticationPrincipal OidcUser user,
        @Valid @RequestBody ReauthVerifyRequest request,
        HttpServletRequest servletRequest
    ) {
        UUID actorId = actor(user);
        AdminApiDtos.InternalProofResponse proof = internalClient.verifyReauth(
            actorId,
            new AdminApiDtos.ReauthVerifyRequest(
                request.method(), request.challengeId(), request.code()
            ),
            traceId()
        );
        servletRequest.changeSessionId();
        HttpSession session = servletRequest.getSession(false);
        elevatedSession.elevate(session, actorId, proof.proof(), proof.expiresAt());
        return elevatedSession.status(session, actorId);
    }

    @GetMapping("/reauth/status")
    ElevatedAdminSessionService.ElevatedStatus reauthStatus(
        @AuthenticationPrincipal OidcUser user,
        HttpSession session
    ) {
        return elevatedSession.status(session, actor(user));
    }

    private UUID actor(OidcUser user) {
        return UUID.fromString(user.getSubject());
    }

    private String traceId() {
        return UUID.randomUUID().toString();
    }

    public record RolesRequest(@NotEmpty Set<@NotBlank String> roles) {
    }
    public record GroupsRequest(@NotNull Set<@NotNull UUID> groupIds) {
    }
    public record BulkStatusRequest(
        @NotEmpty @Size(max = 100) Set<@NotNull UUID> userIds,
        @NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED") String status
    ) {
    }
    public record BulkRolesRequest(
        @NotEmpty @Size(max = 100) Set<@NotNull UUID> userIds,
        @NotEmpty Set<@NotBlank String> roles
    ) {
    }
    public record CreateGroupRequest(@NotBlank @Size(max = 100) String name, UUID parentId) {
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
        @Override public String toString() {
            return "ReauthVerifyRequest[method=" + method + ", challengeId=" + challengeId
                + ", code=<redacted>]";
        }
    }
}
