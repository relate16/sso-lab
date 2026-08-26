package com.ssolab.auth.admin.service;

import com.ssolab.auth.identity.model.AccountStatus;
import com.ssolab.auth.identity.model.RoleName;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AdminIdentityGuard {

    private final UserIdentityRepository userRepository;

    public AdminIdentityGuard(UserIdentityRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserIdentityEntity requireActiveAdmin(UUID actorId) {
        return userRepository.findById(actorId)
            .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
            .filter(user -> user.hasRole(RoleName.ADMIN))
            .orElseThrow(() -> new IllegalArgumentException("admin identity is unavailable"));
    }
}
