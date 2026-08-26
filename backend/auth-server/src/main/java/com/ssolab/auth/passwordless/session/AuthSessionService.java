package com.ssolab.auth.passwordless.session;

import com.ssolab.auth.identity.model.AccountStatus;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import com.ssolab.auth.systemconfig.IdentityPolicyConfig;
import com.ssolab.auth.systemconfig.TypedSystemConfigService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthSessionService {

    private final UserSessionMetadataRepository repository;
    private final UserIdentityRepository userRepository;
    private final TypedSystemConfigService configService;
    private final Clock clock;

    public AuthSessionService(
        UserSessionMetadataRepository repository,
        UserIdentityRepository userRepository,
        TypedSystemConfigService configService,
        Clock clock
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.configService = configService;
        this.clock = clock;
    }

    @Transactional
    public UserSessionMetadataEntity create(
        String sessionId,
        UUID userId,
        AuthenticationMethod method,
        SessionScope scope
    ) {
        return create(sessionId, userId, method, scope, null);
    }

    @Transactional
    public UserSessionMetadataEntity create(
        String sessionId,
        UUID userId,
        AuthenticationMethod method,
        SessionScope scope,
        String userAgent
    ) {
        UserIdentityEntity user = userRepository.findById(userId)
            .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
            .orElseThrow(() -> new IllegalArgumentException("identity is unavailable"));
        IdentityPolicyConfig policy = configService.identityPolicy();
        Instant now = clock.instant();
        Duration absoluteTtl = scope == SessionScope.RECOVERY_ONLY
            ? policy.adminReauthTtl()
            : policy.ssoSessionAbsoluteTimeout();
        UserSessionMetadataEntity metadata = UserSessionMetadataEntity.create(
            sessionId, user, method, scope, deviceLabel(userAgent), now, now.plus(absoluteTtl)
        );
        return repository.saveAndFlush(metadata);
    }

    private String deviceLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown browser";
        }
        String browser = userAgent.contains("Edg/") ? "Edge"
            : userAgent.contains("Chrome/") ? "Chrome"
            : userAgent.contains("Firefox/") ? "Firefox"
            : userAgent.contains("Safari/") ? "Safari" : "Other browser";
        String platform = userAgent.contains("Windows") ? "Windows"
            : userAgent.contains("Android") ? "Android"
            : userAgent.contains("iPhone") || userAgent.contains("iPad") ? "iOS"
            : userAgent.contains("Mac OS") ? "macOS"
            : userAgent.contains("Linux") ? "Linux" : "Unknown OS";
        return browser + " on " + platform;
    }

    @Transactional
    public SessionValidationStatus validateAndTouch(String sessionId, SessionScope requiredScope) {
        UserSessionMetadataEntity metadata = repository.findLockedBySessionId(sessionId)
            .orElse(null);
        if (metadata == null) {
            return SessionValidationStatus.NOT_FOUND;
        }
        if (metadata.getInvalidatedAt() != null) {
            return SessionValidationStatus.INVALIDATED;
        }
        Instant now = clock.instant();
        if (!now.isBefore(metadata.getAbsoluteExpiresAt())) {
            metadata.invalidate(now);
            repository.saveAndFlush(metadata);
            return SessionValidationStatus.ABSOLUTE_EXPIRED;
        }
        Duration idleTimeout = configService.identityPolicy().ssoSessionIdleTimeout();
        if (!now.isBefore(metadata.getLastAccessedAt().plus(idleTimeout))) {
            metadata.invalidate(now);
            repository.saveAndFlush(metadata);
            return SessionValidationStatus.IDLE_EXPIRED;
        }
        if (metadata.getUser().getStatus() != AccountStatus.ACTIVE) {
            metadata.invalidate(now);
            repository.saveAndFlush(metadata);
            return SessionValidationStatus.ACCOUNT_SUSPENDED;
        }
        if (metadata.getScope() != requiredScope) {
            return SessionValidationStatus.WRONG_SCOPE;
        }
        metadata.touch(now);
        repository.saveAndFlush(metadata);
        return SessionValidationStatus.VALID;
    }

    @Transactional
    public void invalidate(String sessionId) {
        repository.findLockedBySessionId(sessionId).ifPresent(metadata -> {
            if (metadata.getInvalidatedAt() == null) {
                metadata.invalidate(clock.instant());
                repository.saveAndFlush(metadata);
            }
        });
    }

    @Transactional
    public void invalidateAll(UUID userId) {
        Instant now = clock.instant();
        var sessions = repository.findByUser_IdAndInvalidatedAtIsNull(userId);
        sessions.forEach(session -> session.invalidate(now));
        repository.saveAllAndFlush(sessions);
    }

    @Transactional(readOnly = true)
    public boolean hasFreshReauthentication(String sessionId, UUID userId) {
        return repository.findById(sessionId)
            .filter(metadata -> metadata.getInvalidatedAt() == null)
            .filter(metadata -> metadata.getUser().getId().equals(userId))
            .filter(metadata -> metadata.getScope() == SessionScope.NORMAL)
            .filter(metadata -> metadata.getUser().getStatus() == AccountStatus.ACTIVE)
            .filter(metadata -> clock.instant().isBefore(
                metadata.getReauthenticatedAt().plus(configService.identityPolicy().adminReauthTtl())
            ))
            .isPresent();
    }
}
