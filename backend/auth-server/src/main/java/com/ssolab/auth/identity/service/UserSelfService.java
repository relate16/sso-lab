package com.ssolab.auth.identity.service;

import com.ssolab.auth.audit.AuditEvent;
import com.ssolab.auth.audit.AuditService;
import com.ssolab.auth.audit.AuditSource;
import com.ssolab.auth.identity.api.UserProfileDtos;
import com.ssolab.auth.identity.model.AccountStatus;
import com.ssolab.auth.identity.model.IdentityGroupEntity;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import com.ssolab.auth.oidc.logout.SessionManagementService;
import com.ssolab.auth.passwordless.totp.TotpCredentialRepository;
import com.ssolab.auth.passwordless.totp.TotpCredentialStatus;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSelfService {

    private final UserIdentityRepository users;
    private final UserIdentityService identities;
    private final IdentityInputNormalizer inputNormalizer;
    private final TotpCredentialRepository totpCredentials;
    private final SessionManagementService sessions;
    private final AuditService audit;
    private final Clock clock;

    public UserSelfService(
        UserIdentityRepository users,
        UserIdentityService identities,
        IdentityInputNormalizer inputNormalizer,
        TotpCredentialRepository totpCredentials,
        SessionManagementService sessions,
        AuditService audit,
        Clock clock
    ) {
        this.users = users;
        this.identities = identities;
        this.inputNormalizer = inputNormalizer;
        this.totpCredentials = totpCredentials;
        this.sessions = sessions;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserProfileDtos.ProfileResponse profile(UUID userId, String currentSessionId) {
        UserIdentityEntity user = active(userId);
        return response(user, currentSessionId);
    }

    @Transactional
    public UserProfileDtos.ProfileResponse changeUsername(
        UUID userId,
        String username,
        String currentSessionId,
        String traceId
    ) {
        UserIdentityEntity user = users.findLockedById(userId)
            .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
            .orElseThrow(() -> new IdentityNotFoundException("identity is unavailable"));
        user.changeUsername(inputNormalizer.normalizeUsername(username), clock.instant());
        users.saveAndFlush(user);
        audit.recordWithinTransaction(
            AuditEvent.USERNAME_CHANGED,
            userId,
            userId,
            true,
            AuditSource.AUTH_WEB,
            traceId
        );
        return response(user, currentSessionId);
    }

    private UserIdentityEntity active(UUID userId) {
        return users.findById(userId)
            .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
            .orElseThrow(() -> new IdentityNotFoundException("identity is unavailable"));
    }

    private UserProfileDtos.ProfileResponse response(
        UserIdentityEntity user,
        String currentSessionId
    ) {
        List<String> roles = user.getRoles().stream()
            .map(role -> role.getName().name())
            .sorted()
            .toList();
        List<String> groups = user.getGroups().stream()
            .map(this::fullPath)
            .sorted()
            .toList();
        boolean totpEnrolled = totpCredentials.existsByUserIdAndStatus(
            user.getId(), TotpCredentialStatus.ACTIVE
        );
        return new UserProfileDtos.ProfileResponse(
            user.getUserId(),
            user.getUsername(),
            identities.decryptEmail(user),
            roles,
            groups,
            totpEnrolled,
            sessions.list(user.getId(), currentSessionId)
        );
    }

    private String fullPath(IdentityGroupEntity group) {
        ArrayDeque<String> segments = new ArrayDeque<>();
        IdentityGroupEntity current = group;
        int depth = 0;
        while (current != null) {
            if (++depth > 100) {
                throw new IllegalStateException("identity group hierarchy exceeds safe depth");
            }
            segments.addFirst(current.getName());
            current = current.getParent();
        }
        return "/" + String.join("/", segments);
    }
}
