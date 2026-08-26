package com.ssolab.auth.systemconfig;

import com.ssolab.auth.identity.model.SystemConfigEntity;
import com.ssolab.auth.identity.repository.SystemConfigRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TypedSystemConfigService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TypedSystemConfigService.class);

    private final SystemConfigRepository repository;
    private final Clock clock;

    public TypedSystemConfigService(SystemConfigRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public <T> T get(SystemConfigDefinition<T> definition) {
        return repository.findById(definition.key())
            .map(config -> parseOrDefault(definition, config.getValue()))
            .orElse(definition.defaultValue());
    }

    @Transactional
    public <T> void update(SystemConfigDefinition<T> definition, T value) {
        String formatted = definition.format(value);
        SystemConfigEntity config = repository.findById(definition.key())
            .orElseGet(() -> SystemConfigEntity.create(definition.key(), formatted, clock.instant()));
        config.update(formatted, clock.instant());
        repository.saveAndFlush(config);
    }

    @Transactional(readOnly = true)
    public IdentityPolicyConfig identityPolicy() {
        return new IdentityPolicyConfig(
            get(SystemConfigDefinitions.SSO_SESSION_IDLE_TIMEOUT),
            get(SystemConfigDefinitions.SSO_SESSION_ABSOLUTE_TIMEOUT),
            get(SystemConfigDefinitions.ACCESS_TOKEN_TTL),
            get(SystemConfigDefinitions.ID_TOKEN_TTL),
            get(SystemConfigDefinitions.REFRESH_TOKEN_TTL),
            get(SystemConfigDefinitions.ADMIN_REAUTH_TTL),
            get(SystemConfigDefinitions.EMAIL_OTP_TTL),
            get(SystemConfigDefinitions.EMAIL_OTP_MAX_ATTEMPTS),
            get(SystemConfigDefinitions.EMAIL_OTP_RESEND_INTERVAL)
        );
    }

    private <T> T parseOrDefault(SystemConfigDefinition<T> definition, String rawValue) {
        try {
            return definition.parse(rawValue);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                "Invalid value for system config {}; using the safe code default",
                definition.key()
            );
            return definition.defaultValue();
        }
    }
}
