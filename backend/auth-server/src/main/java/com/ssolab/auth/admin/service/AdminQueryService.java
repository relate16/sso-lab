package com.ssolab.auth.admin.service;

import com.ssolab.auth.audit.AuditEntity;
import com.ssolab.auth.audit.AuditEvent;
import com.ssolab.auth.audit.AuditRepository;
import com.ssolab.auth.identity.model.AccountStatus;
import com.ssolab.auth.identity.model.IdentityGroupEntity;
import com.ssolab.auth.identity.model.RoleName;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.IdentityGroupRepository;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import com.ssolab.auth.identity.service.IdentityNotFoundException;
import com.ssolab.auth.identity.service.UserIdentityService;
import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminQueryService {

    private static final Set<String> USER_SORTS = Set.of(
        "normalizedUserId", "username", "status", "createdAt", "updatedAt"
    );
    private static final Set<String> AUDIT_SORTS = Set.of("occurredAt", "event", "success");

    private final AdminIdentityGuard guard;
    private final UserIdentityRepository userRepository;
    private final IdentityGroupRepository groupRepository;
    private final UserIdentityService identityService;
    private final AdminEmailMasker emailMasker;
    private final AuditRepository auditRepository;

    public AdminQueryService(
        AdminIdentityGuard guard,
        UserIdentityRepository userRepository,
        IdentityGroupRepository groupRepository,
        UserIdentityService identityService,
        AdminEmailMasker emailMasker,
        AuditRepository auditRepository
    ) {
        this.guard = guard;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.identityService = identityService;
        this.emailMasker = emailMasker;
        this.auditRepository = auditRepository;
    }

    @Transactional(readOnly = true)
    public AdminDtos.DashboardView dashboard(UUID actorId) {
        guard.requireActiveAdmin(actorId);
        Page<AuditEntity> recent = auditRepository.findAll(PageRequest.of(
            0, 5, Sort.by(Sort.Direction.DESC, "occurredAt")
        ));
        Map<UUID, String> labels = identityLabels(recent.getContent());
        return new AdminDtos.DashboardView(
            userRepository.count(),
            userRepository.countByStatus(AccountStatus.ACTIVE),
            userRepository.countByStatus(AccountStatus.SUSPENDED),
            userRepository.countByRoleAndStatus(RoleName.ADMIN, AccountStatus.ACTIVE),
            groupRepository.count(),
            recent.stream().map(audit -> auditView(audit, labels)).toList()
        );
    }

    @Transactional(readOnly = true)
    public AdminDtos.PageResponse<AdminDtos.UserView> users(
        UUID actorId, String query, String status, String role, UUID groupId,
        int page, int size, String sort, String direction
    ) {
        guard.requireActiveAdmin(actorId);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        Sort pageSort = Sort.by(parseDirection(direction), safeSort(sort, USER_SORTS,
            "normalizedUserId"));
        Specification<UserIdentityEntity> spec = Specification.allOf();
        String normalizedQuery = text(query);
        if (normalizedQuery != null) {
            String pattern = "%" + normalizedQuery.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, ignored, builder) -> builder.or(
                builder.like(builder.lower(root.get("normalizedUserId")), pattern),
                builder.like(builder.lower(root.get("username")), pattern)
            ));
        }
        AccountStatus parsedStatus = parseStatus(status);
        if (parsedStatus != null) {
            spec = spec.and((root, ignored, builder) ->
                builder.equal(root.get("status"), parsedStatus));
        }
        RoleName parsedRole = parseRole(role);
        if (parsedRole != null) {
            spec = spec.and((root, queryDefinition, builder) -> {
                queryDefinition.distinct(true);
                return builder.equal(root.join("roles", JoinType.INNER).get("name"), parsedRole);
            });
        }
        if (groupId != null) {
            spec = spec.and((root, queryDefinition, builder) -> {
                queryDefinition.distinct(true);
                return builder.equal(root.join("groups", JoinType.INNER).get("id"), groupId);
            });
        }
        Page<UserIdentityEntity> users = userRepository.findAll(
            spec, PageRequest.of(safePage, safeSize, pageSort)
        );
        return new AdminDtos.PageResponse<>(
            users.stream().map(this::userView).toList(), safePage, safeSize,
            users.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public AdminDtos.UserView user(UUID actorId, UUID userId) {
        guard.requireActiveAdmin(actorId);
        return userView(userRepository.findById(userId).orElseThrow(
            () -> new IdentityNotFoundException("user was not found")
        ));
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.GroupView> groups(UUID actorId) {
        guard.requireActiveAdmin(actorId);
        return groupRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
            .map(this::groupView)
            .sorted(Comparator.comparing(AdminDtos.GroupView::fullPath))
            .toList();
    }

    @Transactional(readOnly = true)
    public AdminDtos.GroupDetailView group(UUID actorId, UUID groupId) {
        guard.requireActiveAdmin(actorId);
        IdentityGroupEntity group = groupRepository.findById(groupId).orElseThrow(
            () -> new IdentityNotFoundException("group was not found")
        );
        List<AdminDtos.GroupMemberView> members = userRepository.findDistinctByGroupsId(
            groupId, Sort.by(Sort.Direction.ASC, "normalizedUserId")
        ).stream().map(user -> new AdminDtos.GroupMemberView(
            user.getId(), user.getUserId(), user.getUsername(), user.getStatus().name()
        )).toList();
        return new AdminDtos.GroupDetailView(groupView(group), members);
    }

    @Transactional(readOnly = true)
    public AdminDtos.PageResponse<AdminDtos.AuditView> audit(
        UUID actorId, String event, Boolean success, Instant from, Instant to,
        int page, int size, String sort, String direction
    ) {
        guard.requireActiveAdmin(actorId);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        Specification<AuditEntity> spec = Specification.allOf();
        AuditEvent parsedEvent = parseEvent(event);
        if (parsedEvent != null) {
            spec = spec.and((root, ignored, builder) ->
                builder.equal(root.get("event"), parsedEvent));
        }
        if (success != null) {
            spec = spec.and((root, ignored, builder) ->
                builder.equal(root.get("success"), success));
        }
        if (from != null) {
            spec = spec.and((root, ignored, builder) ->
                builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, ignored, builder) ->
                builder.lessThanOrEqualTo(root.get("occurredAt"), to));
        }
        Page<AuditEntity> audits = auditRepository.findAll(spec, PageRequest.of(
            safePage, safeSize,
            Sort.by(parseDirection(direction), safeSort(sort, AUDIT_SORTS, "occurredAt"))
        ));
        Map<UUID, String> labels = identityLabels(audits.getContent());
        return new AdminDtos.PageResponse<>(
            audits.stream().map(audit -> auditView(audit, labels)).toList(),
            audits.getNumber(), audits.getSize(), audits.getTotalElements()
        );
    }

    private AdminDtos.UserView userView(UserIdentityEntity user) {
        List<String> roles = user.getRoles().stream()
            .map(role -> role.getName().name()).sorted().toList();
        List<AdminDtos.GroupMembership> groups = user.getGroups().stream()
            .map(group -> new AdminDtos.GroupMembership(
                group.getId(), group.getName(), fullPath(group)
            )).sorted(Comparator.comparing(AdminDtos.GroupMembership::fullPath)).toList();
        String maskedEmail = emailMasker.mask(identityService.decryptEmail(user));
        return new AdminDtos.UserView(
            user.getId(), user.getUserId(), user.getUsername(), maskedEmail,
            user.getStatus().name(), roles, groups, user.getCreatedAt(), user.getUpdatedAt()
        );
    }

    private AdminDtos.GroupView groupView(IdentityGroupEntity group) {
        UUID parentId = group.getParent() == null ? null : group.getParent().getId();
        return new AdminDtos.GroupView(
            group.getId(), group.getName(), parentId, fullPath(group),
            userRepository.countMembersOfGroup(group.getId()),
            group.getCreatedAt(), group.getUpdatedAt()
        );
    }

    private AdminDtos.AuditView auditView(AuditEntity audit, Map<UUID, String> labels) {
        return new AdminDtos.AuditView(
            audit.getId(), audit.getEvent().name(), audit.getActorId(), audit.getTargetId(),
            label(audit.getActorId(), labels, "시스템"),
            label(audit.getTargetId(), labels, "없음"),
            audit.isSuccess(), audit.getSource().name(), audit.getTraceId(), audit.getOccurredAt()
        );
    }

    private Map<UUID, String> identityLabels(List<AuditEntity> audits) {
        Set<UUID> ids = new HashSet<>();
        audits.forEach(audit -> {
            if (audit.getActorId() != null) ids.add(audit.getActorId());
            if (audit.getTargetId() != null) ids.add(audit.getTargetId());
        });
        Map<UUID, String> labels = new HashMap<>();
        userRepository.findAllById(ids).forEach(user -> labels.put(
            user.getId(), user.getUserId() + " · " + user.getUsername()
        ));
        groupRepository.findAllById(ids).forEach(group -> labels.putIfAbsent(
            group.getId(), "그룹 " + fullPath(group)
        ));
        return labels;
    }

    private String label(UUID id, Map<UUID, String> labels, String absent) {
        if (id == null) return absent;
        return labels.getOrDefault(id, "삭제된 사용자");
    }

    private String fullPath(IdentityGroupEntity group) {
        ArrayDeque<String> segments = new ArrayDeque<>();
        IdentityGroupEntity current = group;
        int depth = 0;
        while (current != null) {
            if (++depth > 100) throw new IllegalStateException(
                "identity group hierarchy exceeds safe depth");
            segments.addFirst(current.getName());
            current = current.getParent();
        }
        return "/" + String.join("/", segments);
    }

    private String safeSort(String requested, Set<String> allowed, String fallback) {
        return requested != null && allowed.contains(requested) ? requested : fallback;
    }

    private Sort.Direction parseDirection(String direction) {
        return "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AccountStatus parseStatus(String value) {
        String candidate = text(value);
        if (candidate == null) return null;
        try {
            return AccountStatus.valueOf(candidate.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported account status", exception);
        }
    }

    private RoleName parseRole(String value) {
        String candidate = text(value);
        if (candidate == null) return null;
        try {
            return RoleName.valueOf(candidate.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported role", exception);
        }
    }

    private AuditEvent parseEvent(String value) {
        String candidate = text(value);
        if (candidate == null) return null;
        try {
            return AuditEvent.valueOf(candidate.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported audit event", exception);
        }
    }
}
