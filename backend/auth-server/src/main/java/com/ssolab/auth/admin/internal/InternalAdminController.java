package com.ssolab.auth.admin.internal;

import com.ssolab.auth.admin.service.AdminDtos;
import com.ssolab.auth.admin.service.AdminManagementService;
import com.ssolab.auth.admin.service.AdminQueryService;
import com.ssolab.auth.admin.service.AdminReauthService;
import com.ssolab.auth.security.ratelimit.RateLimitAction;
import com.ssolab.auth.security.ratelimit.SecurityRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/v1")
public class InternalAdminController {

    public static final String ACTOR_HEADER = "X-Admin-Actor-Id";
    public static final String TRACE_HEADER = "X-Trace-Id";

    private final AdminQueryService queryService;
    private final AdminManagementService managementService;
    private final AdminReauthService reauthService;
    private final SecurityRateLimitService rateLimits;

    public InternalAdminController(
        AdminQueryService queryService,
        AdminManagementService managementService,
        AdminReauthService reauthService,
        SecurityRateLimitService rateLimits
    ) {
        this.queryService = queryService;
        this.managementService = managementService;
        this.reauthService = reauthService;
        this.rateLimits = rateLimits;
    }

    @GetMapping("/dashboard")
    AdminDtos.DashboardView dashboard(@RequestHeader(ACTOR_HEADER) UUID actorId) {
        return queryService.dashboard(actorId);
    }

    @GetMapping("/users")
    AdminDtos.PageResponse<AdminDtos.UserView> users(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String role,
        @RequestParam(required = false) UUID groupId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size,
        @RequestParam(defaultValue = "normalizedUserId") String sort,
        @RequestParam(defaultValue = "asc") String direction
    ) {
        return queryService.users(
            actorId, q, status, role, groupId, page, size, sort, direction
        );
    }

    @GetMapping("/users/{userId}")
    AdminDtos.UserView user(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @PathVariable UUID userId
    ) {
        return queryService.user(actorId, userId);
    }

    @PostMapping("/users/{userId}/suspend")
    void suspend(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @RequestHeader(value = TRACE_HEADER, required = false) String traceId,
        @PathVariable UUID userId
    ) {
        managementService.suspend(actorId, userId, traceId);
    }

    @PostMapping("/users/{userId}/resume")
    void resume(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @RequestHeader(value = TRACE_HEADER, required = false) String traceId,
        @PathVariable UUID userId
    ) {
        managementService.resume(actorId, userId, traceId);
    }

    @PutMapping("/users/{userId}/roles")
    void roles(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @RequestHeader(value = TRACE_HEADER, required = false) String traceId,
        @PathVariable UUID userId,
        @Valid @RequestBody AdminDtos.RolesRequest request
    ) {
        managementService.replaceRoles(actorId, userId, request.roles(), traceId);
    }

    @PutMapping("/users/{userId}/groups")
    void groups(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @RequestHeader(value = TRACE_HEADER, required = false) String traceId,
        @PathVariable UUID userId,
        @Valid @RequestBody AdminDtos.GroupsRequest request
    ) {
        managementService.replaceGroups(actorId, userId, request.groupIds(), traceId);
    }

    @PostMapping("/users/bulk/status")
    AdminDtos.BulkResult bulkStatus(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @RequestHeader(value = TRACE_HEADER, required = false) String traceId,
        @Valid @RequestBody AdminDtos.BulkStatusRequest request
    ) {
        return managementService.bulkStatus(
            actorId, request.userIds(), request.status(), traceId
        );
    }

    @PutMapping("/users/bulk/roles")
    AdminDtos.BulkResult bulkRoles(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @RequestHeader(value = TRACE_HEADER, required = false) String traceId,
        @Valid @RequestBody AdminDtos.BulkRolesRequest request
    ) {
        return managementService.bulkRoles(
            actorId, request.userIds(), request.roles(), traceId
        );
    }

    @PostMapping("/users/{userId}/email/reveal")
    AdminDtos.EmailRevealResponse revealEmail(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @RequestHeader(value = TRACE_HEADER, required = false) String traceId,
        @PathVariable UUID userId,
        @Valid @RequestBody AdminDtos.EmailRevealRequest request
    ) {
        return reauthService.revealEmail(actorId, userId, request.proof(), traceId);
    }

    @GetMapping("/groups")
    List<AdminDtos.GroupView> groups(@RequestHeader(ACTOR_HEADER) UUID actorId) {
        return queryService.groups(actorId);
    }

    @GetMapping("/groups/{groupId}")
    AdminDtos.GroupDetailView group(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @PathVariable UUID groupId
    ) {
        return queryService.group(actorId, groupId);
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> createGroup(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @RequestHeader(value = TRACE_HEADER, required = false) String traceId,
        @Valid @RequestBody AdminDtos.CreateGroupRequest request
    ) {
        return Map.of("id", managementService.createGroup(
            actorId, request.name(), request.parentId(), traceId
        ));
    }

    @PatchMapping("/groups/{groupId}")
    void renameGroup(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @RequestHeader(value = TRACE_HEADER, required = false) String traceId,
        @PathVariable UUID groupId,
        @Valid @RequestBody AdminDtos.RenameGroupRequest request
    ) {
        managementService.renameGroup(actorId, groupId, request.name(), traceId);
    }

    @PostMapping("/groups/{groupId}/move")
    void moveGroup(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @RequestHeader(value = TRACE_HEADER, required = false) String traceId,
        @PathVariable UUID groupId,
        @RequestBody AdminDtos.MoveGroupRequest request
    ) {
        managementService.moveGroup(actorId, groupId, request.parentId(), traceId);
    }

    @DeleteMapping("/groups/{groupId}")
    void deleteGroup(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @RequestHeader(value = TRACE_HEADER, required = false) String traceId,
        @PathVariable UUID groupId
    ) {
        managementService.deleteGroup(actorId, groupId, traceId);
    }

    @PostMapping("/groups/{groupId}/members/{userId}")
    void addGroupMember(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @RequestHeader(value = TRACE_HEADER, required = false) String traceId,
        @PathVariable UUID groupId,
        @PathVariable UUID userId
    ) {
        managementService.addGroupMember(actorId, groupId, userId, traceId);
    }

    @DeleteMapping("/groups/{groupId}/members/{userId}")
    void removeGroupMember(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @RequestHeader(value = TRACE_HEADER, required = false) String traceId,
        @PathVariable UUID groupId,
        @PathVariable UUID userId
    ) {
        managementService.removeGroupMember(actorId, groupId, userId, traceId);
    }

    @PostMapping("/reauth/email/start")
    AdminDtos.ReauthStartResponse startReauth(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        HttpServletRequest servletRequest
    ) {
        rateLimits.check(RateLimitAction.ADMIN_REAUTH, servletRequest.getRemoteAddr(),
            actorId.toString(), null);
        return reauthService.startEmail(actorId);
    }

    @PostMapping("/reauth/verify")
    AdminDtos.ReauthProofResponse verifyReauth(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @RequestHeader(value = TRACE_HEADER, required = false) String traceId,
        @Valid @RequestBody AdminDtos.ReauthVerifyRequest request,
        HttpServletRequest servletRequest
    ) {
        rateLimits.check(RateLimitAction.ADMIN_REAUTH, servletRequest.getRemoteAddr(),
            actorId.toString(), request.challengeId() == null
                ? null : request.challengeId().toString());
        return reauthService.verify(actorId, request, traceId);
    }

    @GetMapping("/audit-logs")
    AdminDtos.PageResponse<AdminDtos.AuditView> audit(
        @RequestHeader(ACTOR_HEADER) UUID actorId,
        @RequestParam(required = false) String event,
        @RequestParam(required = false) Boolean success,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(defaultValue = "occurredAt") String sort,
        @RequestParam(defaultValue = "desc") String direction
    ) {
        return queryService.audit(
            actorId, event, success, from, to, page, size, sort, direction
        );
    }
}
