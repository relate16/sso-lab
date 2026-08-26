package com.ssolab.auth.admin.bootstrap;

import com.ssolab.auth.admin.audit.AdminAuditEvent;
import com.ssolab.auth.admin.audit.AdminAuditService;
import com.ssolab.auth.admin.audit.AdminAuditSource;
import com.ssolab.auth.identity.crypto.EmailLookupHasher;
import com.ssolab.auth.identity.crypto.EmailNormalizer;
import com.ssolab.auth.identity.model.RoleEntity;
import com.ssolab.auth.identity.model.RoleName;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.RoleRepository;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BootstrapAdminService {

    private final BootstrapAdminProperties properties;
    private final BootstrapAdminStateRepository stateRepository;
    private final RoleRepository roleRepository;
    private final EmailNormalizer emailNormalizer;
    private final EmailLookupHasher emailLookupHasher;
    private final AdminAuditService auditService;
    private final Clock clock;

    public BootstrapAdminService(
        BootstrapAdminProperties properties,
        BootstrapAdminStateRepository stateRepository,
        RoleRepository roleRepository,
        EmailNormalizer emailNormalizer,
        EmailLookupHasher emailLookupHasher,
        AdminAuditService auditService,
        Clock clock
    ) {
        this.properties = properties;
        this.stateRepository = stateRepository;
        this.roleRepository = roleRepository;
        this.emailNormalizer = emailNormalizer;
        this.emailLookupHasher = emailLookupHasher;
        this.auditService = auditService;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initialize() {
        if (!properties.enabled()) {
            return;
        }
        byte[] configuredHash = configuredHash();
        try {
            BootstrapAdminStateEntity existing = stateRepository
                .findById(BootstrapAdminStateEntity.SINGLETON_ID).orElse(null);
            if (existing == null) {
                stateRepository.saveAndFlush(
                    BootstrapAdminStateEntity.pending(configuredHash, clock.instant())
                );
                return;
            }
            if (!MessageDigest.isEqual(existing.getEmailLookupHash(), configuredHash)) {
                throw new IllegalStateException(
                    "bootstrap admin configuration cannot replace existing bootstrap state"
                );
            }
        } finally {
            Arrays.fill(configuredHash, (byte) 0);
        }
    }

    @Transactional
    public boolean claimIfEligible(UserIdentityEntity user) {
        if (!properties.enabled()) {
            return false;
        }
        BootstrapAdminStateEntity state = stateRepository.findLocked().orElseThrow(
            () -> new IllegalStateException("bootstrap admin state is not initialized")
        );
        if (state.isClaimed()
            || !MessageDigest.isEqual(state.getEmailLookupHash(), user.getEmailLookupHash())) {
            return false;
        }
        RoleEntity adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow(
            () -> new IllegalStateException("seeded ADMIN role is missing")
        );
        user.addRole(adminRole);
        Instant now = clock.instant();
        state.claim(user.getId(), now);
        stateRepository.saveAndFlush(state);
        auditService.record(
            AdminAuditEvent.BOOTSTRAP_ADMIN_CLAIMED,
            user.getId(), user.getId(), true, AdminAuditSource.BOOTSTRAP, null
        );
        return true;
    }

    private byte[] configuredHash() {
        String normalized = emailNormalizer.normalize(properties.email());
        return emailLookupHasher.hash(normalized);
    }
}
