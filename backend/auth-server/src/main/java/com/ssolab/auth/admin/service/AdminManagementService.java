package com.ssolab.auth.admin.service;

import com.ssolab.auth.admin.audit.AdminAuditEvent;
import com.ssolab.auth.admin.audit.AdminAuditService;
import com.ssolab.auth.admin.audit.AdminAuditSource;
import com.ssolab.auth.identity.model.AccountStatus;
import com.ssolab.auth.identity.model.IdentityGroupEntity;
import com.ssolab.auth.identity.model.RoleEntity;
import com.ssolab.auth.identity.model.RoleName;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.IdentityGroupRepository;
import com.ssolab.auth.identity.repository.RoleRepository;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import com.ssolab.auth.identity.service.GroupHierarchyService;
import com.ssolab.auth.identity.service.IdentityConflictException;
import com.ssolab.auth.identity.service.IdentityNotFoundException;
import java.time.Clock;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminManagementService {

    private final AdminIdentityGuard guard;
    private final UserIdentityRepository userRepository;
    private final RoleRepository roleRepository;
    private final IdentityGroupRepository groupRepository;
    private final GroupHierarchyService groupHierarchyService;
    private final AdminCredentialRevocationService revocationService;
    private final AdminAuditService auditService;
    private final Clock clock;

    public AdminManagementService(
        AdminIdentityGuard guard,
        UserIdentityRepository userRepository,
        RoleRepository roleRepository,
        IdentityGroupRepository groupRepository,
        GroupHierarchyService groupHierarchyService,
        AdminCredentialRevocationService revocationService,
        AdminAuditService auditService,
        Clock clock
    ) {
        this.guard = guard;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.groupRepository = groupRepository;
        this.groupHierarchyService = groupHierarchyService;
        this.revocationService = revocationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public void suspend(UUID actorId, UUID targetId, String traceId) {
        guard.requireActiveAdmin(actorId);
        UserIdentityEntity target = requireLockedUser(targetId);
        if (target.getStatus() == AccountStatus.SUSPENDED) {
            return;
        }
        if (target.hasRole(RoleName.ADMIN)
            && userRepository.countByRoleAndStatus(RoleName.ADMIN, AccountStatus.ACTIVE) <= 1) {
            throw new IdentityConflictException("the last active ADMIN cannot be suspended");
        }
        target.suspend(clock.instant());
        userRepository.saveAndFlush(target);
        revocationService.revokeAll(targetId);
        audit(AdminAuditEvent.ACCOUNT_SUSPENDED, actorId, targetId, traceId);
    }

    @Transactional
    public void resume(UUID actorId, UUID targetId, String traceId) {
        guard.requireActiveAdmin(actorId);
        UserIdentityEntity target = requireLockedUser(targetId);
        if (target.getStatus() == AccountStatus.ACTIVE) {
            return;
        }
        target.activate(clock.instant());
        userRepository.saveAndFlush(target);
        audit(AdminAuditEvent.ACCOUNT_RESUMED, actorId, targetId, traceId);
    }

    @Transactional
    public void replaceRoles(
        UUID actorId,
        UUID targetId,
        Set<String> roleValues,
        String traceId
    ) {
        guard.requireActiveAdmin(actorId);
        UserIdentityEntity target = requireLockedUser(targetId);
        EnumSet<RoleName> desired = parseRoles(roleValues);
        if (!desired.contains(RoleName.USER)) {
            throw new IdentityConflictException("the mandatory USER role is required");
        }
        EnumSet<RoleName> current = target.getRoles().stream()
            .map(RoleEntity::getName)
            .collect(() -> EnumSet.noneOf(RoleName.class), EnumSet::add, EnumSet::addAll);
        if (current.equals(desired)) {
            return;
        }
        if (current.contains(RoleName.ADMIN) && !desired.contains(RoleName.ADMIN)
            && target.getStatus() == AccountStatus.ACTIVE
            && userRepository.countByRoleAndStatus(RoleName.ADMIN, AccountStatus.ACTIVE) <= 1) {
            throw new IdentityConflictException("the last active ADMIN role cannot be removed");
        }

        for (RoleName removed : difference(current, desired)) {
            target.removeRole(requireRole(removed));
            audit(AdminAuditEvent.ROLE_REMOVED, actorId, targetId, traceId);
        }
        for (RoleName added : difference(desired, current)) {
            target.addRole(requireRole(added));
            audit(AdminAuditEvent.ROLE_ASSIGNED, actorId, targetId, traceId);
        }
        userRepository.saveAndFlush(target);
        revocationService.revokeAll(targetId);
    }

    @Transactional
    public void replaceGroups(
        UUID actorId,
        UUID targetId,
        Set<UUID> groupIds,
        String traceId
    ) {
        guard.requireActiveAdmin(actorId);
        UserIdentityEntity target = requireLockedUser(targetId);
        Set<IdentityGroupEntity> desired = new HashSet<>(groupRepository.findAllById(groupIds));
        if (desired.size() != groupIds.size()) {
            throw new IdentityNotFoundException("one or more groups were not found");
        }
        Set<IdentityGroupEntity> current = new HashSet<>(target.getGroups());
        current.stream().filter(group -> !desired.contains(group)).forEach(group -> {
            target.removeGroup(group);
            audit(AdminAuditEvent.GROUP_REMOVED, actorId, targetId, traceId);
        });
        desired.stream().filter(group -> !current.contains(group)).forEach(group -> {
            target.addGroup(group);
            audit(AdminAuditEvent.GROUP_ASSIGNED, actorId, targetId, traceId);
        });
        userRepository.saveAndFlush(target);
    }

    @Transactional
    public UUID createGroup(
        UUID actorId,
        String name,
        UUID parentId,
        String traceId
    ) {
        guard.requireActiveAdmin(actorId);
        IdentityGroupEntity group = groupHierarchyService.createGroup(name, parentId);
        audit(AdminAuditEvent.GROUP_CREATED, actorId, group.getId(), traceId);
        return group.getId();
    }

    @Transactional
    public void renameGroup(UUID actorId, UUID groupId, String name, String traceId) {
        guard.requireActiveAdmin(actorId);
        groupHierarchyService.renameGroup(groupId, name);
        audit(AdminAuditEvent.GROUP_UPDATED, actorId, groupId, traceId);
    }

    @Transactional
    public void moveGroup(UUID actorId, UUID groupId, UUID parentId, String traceId) {
        guard.requireActiveAdmin(actorId);
        groupHierarchyService.moveGroup(groupId, parentId);
        audit(AdminAuditEvent.GROUP_MOVED, actorId, groupId, traceId);
    }

    @Transactional
    public void deleteGroup(UUID actorId, UUID groupId, String traceId) {
        guard.requireActiveAdmin(actorId);
        groupHierarchyService.deleteGroup(groupId);
        audit(AdminAuditEvent.GROUP_DELETED, actorId, groupId, traceId);
    }

    private UserIdentityEntity requireLockedUser(UUID userId) {
        return userRepository.findLockedById(userId).orElseThrow(
            () -> new IdentityNotFoundException("user was not found")
        );
    }

    private RoleEntity requireRole(RoleName roleName) {
        return roleRepository.findByName(roleName).orElseThrow(
            () -> new IllegalStateException("seeded role is missing: " + roleName)
        );
    }

    private EnumSet<RoleName> parseRoles(Set<String> values) {
        try {
            EnumSet<RoleName> parsed = EnumSet.noneOf(RoleName.class);
            values.forEach(value -> parsed.add(RoleName.valueOf(value)));
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported role", exception);
        }
    }

    private Set<RoleName> difference(Set<RoleName> left, Set<RoleName> right) {
        EnumSet<RoleName> result = EnumSet.copyOf(left);
        result.removeAll(right);
        return result;
    }

    private void audit(
        AdminAuditEvent event,
        UUID actorId,
        UUID targetId,
        String traceId
    ) {
        auditService.record(
            event, actorId, targetId, true, AdminAuditSource.ADMIN_WEB, traceId
        );
    }
}
