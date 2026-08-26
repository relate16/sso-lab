package com.ssolab.auth.oidc.logout;

import com.ssolab.auth.passwordless.session.SessionScope;
import com.ssolab.auth.passwordless.session.UserSessionMetadataRepository;
import com.ssolab.auth.systemconfig.TypedSystemConfigService;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionManagementService {
    private final UserSessionMetadataRepository repository;
    private final TypedSystemConfigService config;
    private final Clock clock;

    public SessionManagementService(UserSessionMetadataRepository repository,
        TypedSystemConfigService config, Clock clock) {
        this.repository = repository;
        this.config = config;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SessionView> list(UUID userId, String currentSessionId) {
        return repository.findByUser_IdAndInvalidatedAtIsNull(userId).stream()
            .filter(value -> value.getScope() == SessionScope.NORMAL)
            .filter(value -> clock.instant().isBefore(value.getAbsoluteExpiresAt()))
            .filter(value -> clock.instant().isBefore(value.getLastAccessedAt()
                .plus(config.identityPolicy().ssoSessionIdleTimeout())))
            .sorted(Comparator.comparing(value -> value.getCreatedAt(), Comparator.reverseOrder()))
            .map(value -> new SessionView(value.getPublicId(), value.getDeviceLabel(),
                value.getAuthenticationMethod().name(), value.getCreatedAt(),
                value.getLastAccessedAt(), value.getAbsoluteExpiresAt(),
                value.getSessionId().equals(currentSessionId)))
            .toList();
    }
}
