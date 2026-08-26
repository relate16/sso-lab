package com.ssolab.auth.identity.service;

import com.ssolab.auth.identity.model.AccountStatus;
import com.ssolab.auth.identity.model.IdentityGroupEntity;
import com.ssolab.auth.identity.model.RoleEntity;
import com.ssolab.auth.identity.model.RoleName;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.IdentityGroupRepository;
import com.ssolab.auth.identity.repository.RoleRepository;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityMembershipService {

    private final UserIdentityRepository userRepository;
    private final RoleRepository roleRepository;
    private final IdentityGroupRepository groupRepository;

    public IdentityMembershipService(
        UserIdentityRepository userRepository,
        RoleRepository roleRepository,
        IdentityGroupRepository groupRepository
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.groupRepository = groupRepository;
    }

    @Transactional
    public void assignRole(UUID userId, RoleName roleName) {
        UserIdentityEntity user = requireUser(userId);
        RoleEntity role = requireRole(roleName);
        user.addRole(role);
    }

    @Transactional
    public void removeRole(UUID userId, RoleName roleName) {
        UserIdentityEntity user = requireUser(userId);
        if (roleName == RoleName.USER) {
            throw new IdentityConflictException("the mandatory USER role cannot be removed");
        }
        if (roleName == RoleName.ADMIN && user.hasRole(RoleName.ADMIN)
            && user.getStatus() == AccountStatus.ACTIVE
            && userRepository.countByRoleAndStatus(RoleName.ADMIN, AccountStatus.ACTIVE) <= 1) {
            throw new IdentityConflictException("the last active ADMIN role cannot be removed");
        }
        user.removeRole(requireRole(roleName));
    }

    @Transactional
    public void assignGroup(UUID userId, UUID groupId) {
        UserIdentityEntity user = requireUser(userId);
        IdentityGroupEntity group = requireGroup(groupId);
        user.addGroup(group);
    }

    @Transactional
    public void removeGroup(UUID userId, UUID groupId) {
        UserIdentityEntity user = requireUser(userId);
        user.removeGroup(requireGroup(groupId));
    }

    private UserIdentityEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new IdentityNotFoundException("user was not found"));
    }

    private RoleEntity requireRole(RoleName roleName) {
        return roleRepository.findByName(roleName)
            .orElseThrow(() -> new IllegalStateException("seeded role is missing: " + roleName));
    }

    private IdentityGroupEntity requireGroup(UUID groupId) {
        return groupRepository.findById(groupId)
            .orElseThrow(() -> new IdentityNotFoundException("group was not found"));
    }
}
