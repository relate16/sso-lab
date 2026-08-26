package com.ssolab.auth.admin.reauth;

import com.ssolab.auth.identity.model.AccountStatus;
import com.ssolab.auth.identity.model.RoleName;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import com.ssolab.auth.systemconfig.TypedSystemConfigService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminReauthProofService {

    private final AdminReauthProofRepository repository;
    private final UserIdentityRepository userRepository;
    private final TypedSystemConfigService configService;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public AdminReauthProofService(
        AdminReauthProofRepository repository,
        UserIdentityRepository userRepository,
        TypedSystemConfigService configService,
        Clock clock
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.configService = configService;
        this.secureRandom = new SecureRandom();
        this.clock = clock;
    }

    @Transactional
    public AdminReauthProof issue(UUID actorId, AdminReauthMethod method) {
        UserIdentityEntity actor = requireActiveAdmin(actorId);
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        java.util.Arrays.fill(random, (byte) 0);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(configService.identityPolicy().adminReauthTtl());
        repository.saveAndFlush(AdminReauthProofEntity.create(
            actor, digest(value), method, now, expiresAt
        ));
        return new AdminReauthProof(value, expiresAt);
    }

    @Transactional(readOnly = true)
    public boolean validate(UUID actorId, String proofValue) {
        if (proofValue == null || proofValue.isBlank()) {
            return false;
        }
        UserIdentityEntity actor = requireActiveAdmin(actorId);
        return repository.findByProofHash(digest(proofValue))
            .filter(proof -> proof.getActor().getId().equals(actor.getId()))
            .filter(proof -> proof.isValidAt(clock.instant()))
            .isPresent();
    }

    private UserIdentityEntity requireActiveAdmin(UUID actorId) {
        return userRepository.findById(actorId)
            .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
            .filter(user -> user.hasRole(RoleName.ADMIN))
            .orElseThrow(() -> new IllegalArgumentException("admin identity is unavailable"));
    }

    private byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
