package com.ssolab.admin.internal;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InternalAdminClient {

    private static final String ACTOR_HEADER = "X-Admin-Actor-Id";
    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String CLIENT_HEADER = "X-Internal-Client-Id";
    private static final String SECRET_HEADER = "X-Internal-Service-Secret";

    private final RestClient client;
    private final InternalAdminProperties properties;

    public InternalAdminClient(InternalAdminProperties properties) {
        this.properties = properties;
        this.client = RestClient.builder().baseUrl(properties.baseUri().toString()).build();
    }

    public AdminApiDtos.DashboardView dashboard(UUID actorId) {
        return headers(client.get().uri("/dashboard"), actorId, null)
            .retrieve().body(AdminApiDtos.DashboardView.class);
    }

    public AdminApiDtos.PageResponse<AdminApiDtos.UserView> users(
        UUID actorId, String query, String status, String role, UUID groupId,
        int page, int size, String sort, String direction
    ) {
        return headers(client.get().uri(uri -> uri.path("/users")
                .queryParamIfPresent("q", Optional.ofNullable(query))
                .queryParamIfPresent("status", Optional.ofNullable(status))
                .queryParamIfPresent("role", Optional.ofNullable(role))
                .queryParamIfPresent("groupId", Optional.ofNullable(groupId))
                .queryParam("page", page).queryParam("size", size)
                .queryParam("sort", sort).queryParam("direction", direction).build()),
            actorId, null)
            .retrieve().body(new ParameterizedTypeReference<>() { });
    }

    public AdminApiDtos.UserView user(UUID actorId, UUID userId) {
        return headers(client.get().uri("/users/{id}", userId), actorId, null)
            .retrieve().body(AdminApiDtos.UserView.class);
    }

    public void suspend(UUID actorId, UUID userId, String traceId) {
        postEmpty("/users/{id}/suspend", actorId, userId, traceId);
    }

    public void resume(UUID actorId, UUID userId, String traceId) {
        postEmpty("/users/{id}/resume", actorId, userId, traceId);
    }

    public void roles(UUID actorId, UUID userId, AdminApiDtos.RolesRequest request, String traceId) {
        headers(client.put().uri("/users/{id}/roles", userId)
            .contentType(MediaType.APPLICATION_JSON).body(request), actorId, traceId)
            .retrieve().toBodilessEntity();
    }

    public void groups(UUID actorId, UUID userId, AdminApiDtos.GroupsRequest request, String traceId) {
        headers(client.put().uri("/users/{id}/groups", userId)
            .contentType(MediaType.APPLICATION_JSON).body(request), actorId, traceId)
            .retrieve().toBodilessEntity();
    }

    public AdminApiDtos.BulkResult bulkStatus(
        UUID actorId, AdminApiDtos.BulkStatusRequest request, String traceId
    ) {
        return headers(client.post().uri("/users/bulk/status")
            .contentType(MediaType.APPLICATION_JSON).body(request), actorId, traceId)
            .retrieve().body(AdminApiDtos.BulkResult.class);
    }

    public AdminApiDtos.BulkResult bulkRoles(
        UUID actorId, AdminApiDtos.BulkRolesRequest request, String traceId
    ) {
        return headers(client.put().uri("/users/bulk/roles")
            .contentType(MediaType.APPLICATION_JSON).body(request), actorId, traceId)
            .retrieve().body(AdminApiDtos.BulkResult.class);
    }

    public List<AdminApiDtos.GroupView> groups(UUID actorId) {
        return headers(client.get().uri("/groups"), actorId, null).retrieve()
            .body(new ParameterizedTypeReference<>() { });
    }

    public AdminApiDtos.GroupDetailView group(UUID actorId, UUID groupId) {
        return headers(client.get().uri("/groups/{id}", groupId), actorId, null)
            .retrieve().body(AdminApiDtos.GroupDetailView.class);
    }

    public AdminApiDtos.CreatedId createGroup(
        UUID actorId, AdminApiDtos.CreateGroupRequest request, String traceId
    ) {
        return headers(client.post().uri("/groups")
            .contentType(MediaType.APPLICATION_JSON).body(request), actorId, traceId).retrieve()
            .body(AdminApiDtos.CreatedId.class);
    }

    public void renameGroup(
        UUID actorId, UUID groupId, AdminApiDtos.RenameGroupRequest request, String traceId
    ) {
        headers(client.patch().uri("/groups/{id}", groupId)
            .contentType(MediaType.APPLICATION_JSON).body(request), actorId, traceId)
            .retrieve().toBodilessEntity();
    }

    public void moveGroup(
        UUID actorId, UUID groupId, AdminApiDtos.MoveGroupRequest request, String traceId
    ) {
        headers(client.post().uri("/groups/{id}/move", groupId)
            .contentType(MediaType.APPLICATION_JSON).body(request), actorId, traceId)
            .retrieve().toBodilessEntity();
    }

    public void deleteGroup(UUID actorId, UUID groupId, String traceId) {
        headers(client.delete().uri("/groups/{id}", groupId), actorId, traceId)
            .retrieve().toBodilessEntity();
    }

    public void addGroupMember(UUID actorId, UUID groupId, UUID userId, String traceId) {
        headers(client.post().uri("/groups/{groupId}/members/{userId}", groupId, userId),
            actorId, traceId).retrieve().toBodilessEntity();
    }

    public void removeGroupMember(UUID actorId, UUID groupId, UUID userId, String traceId) {
        headers(client.delete().uri("/groups/{groupId}/members/{userId}", groupId, userId),
            actorId, traceId).retrieve().toBodilessEntity();
    }

    public AdminApiDtos.PageResponse<AdminApiDtos.AuditView> audit(
        UUID actorId, String event, Boolean success, Instant from, Instant to,
        int page, int size, String sort, String direction
    ) {
        return headers(client.get().uri(uri -> uri.path("/audit-logs")
                .queryParamIfPresent("event", Optional.ofNullable(event))
                .queryParamIfPresent("success", Optional.ofNullable(success))
                .queryParamIfPresent("from", Optional.ofNullable(from))
                .queryParamIfPresent("to", Optional.ofNullable(to))
                .queryParam("page", page).queryParam("size", size)
                .queryParam("sort", sort).queryParam("direction", direction).build()),
            actorId, null)
            .retrieve().body(new ParameterizedTypeReference<>() { });
    }

    public AdminApiDtos.ReauthStartResponse startReauth(UUID actorId) {
        return headers(client.post().uri("/reauth/email/start"), actorId, null)
            .retrieve().body(AdminApiDtos.ReauthStartResponse.class);
    }

    public AdminApiDtos.InternalProofResponse verifyReauth(
        UUID actorId, AdminApiDtos.ReauthVerifyRequest request, String traceId
    ) {
        return headers(client.post().uri("/reauth/verify")
            .contentType(MediaType.APPLICATION_JSON).body(request), actorId, traceId).retrieve()
            .body(AdminApiDtos.InternalProofResponse.class);
    }

    public AdminApiDtos.EmailRevealResponse revealEmail(
        UUID actorId, UUID userId, String proof, String traceId
    ) {
        return headers(client.post().uri("/users/{id}/email/reveal", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new AdminApiDtos.EmailRevealRequest(proof)), actorId, traceId).retrieve()
            .body(AdminApiDtos.EmailRevealResponse.class);
    }

    private void postEmpty(String path, UUID actorId, UUID targetId, String traceId) {
        headers(client.post().uri(path, targetId), actorId, traceId)
            .retrieve().toBodilessEntity();
    }

    private RestClient.RequestHeadersSpec<?> headers(
        RestClient.RequestHeadersSpec<?> request,
        UUID actorId,
        String traceId
    ) {
        request.header(CLIENT_HEADER, properties.clientId())
            .header(SECRET_HEADER, properties.serviceSecret())
            .header(ACTOR_HEADER, actorId.toString());
        if (traceId != null && !traceId.isBlank()) {
            request.header(TRACE_HEADER, traceId);
        }
        return request;
    }
}
