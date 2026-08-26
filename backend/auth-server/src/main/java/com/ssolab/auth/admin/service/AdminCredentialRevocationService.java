package com.ssolab.auth.admin.service;

import com.ssolab.auth.oidc.logout.LogoutCoordinator;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCredentialRevocationService {

    private final LogoutCoordinator logoutCoordinator;

    public AdminCredentialRevocationService(
        LogoutCoordinator logoutCoordinator
    ) {
        this.logoutCoordinator = logoutCoordinator;
    }

    @Transactional
    public void revokeAll(UUID userId) {
        logoutCoordinator.logoutAll(userId, null);
    }
}
