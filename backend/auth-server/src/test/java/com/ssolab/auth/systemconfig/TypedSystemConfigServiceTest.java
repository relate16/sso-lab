package com.ssolab.auth.systemconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ssolab.auth.identity.model.SystemConfigEntity;
import com.ssolab.auth.identity.repository.SystemConfigRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TypedSystemConfigServiceTest {

    @Mock
    SystemConfigRepository repository;

    private final Clock clock = Clock.fixed(
        Instant.parse("2026-08-20T00:00:00Z"),
        ZoneOffset.UTC
    );

    @Test
    void returnsTypedDatabaseValue() {
        SystemConfigEntity entity = SystemConfigEntity.create(
            SystemConfigDefinitions.ACCESS_TOKEN_TTL.key(),
            "10m",
            clock.instant()
        );
        when(repository.findById(SystemConfigDefinitions.ACCESS_TOKEN_TTL.key()))
            .thenReturn(Optional.of(entity));

        TypedSystemConfigService service = new TypedSystemConfigService(repository, clock);

        assertThat(service.get(SystemConfigDefinitions.ACCESS_TOKEN_TTL))
            .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void fallsBackToSafeDefaultForInvalidDatabaseValue() {
        SystemConfigEntity entity = SystemConfigEntity.create(
            SystemConfigDefinitions.ACCESS_TOKEN_TTL.key(),
            "999h",
            clock.instant()
        );
        when(repository.findById(SystemConfigDefinitions.ACCESS_TOKEN_TTL.key()))
            .thenReturn(Optional.of(entity));

        TypedSystemConfigService service = new TypedSystemConfigService(repository, clock);

        assertThat(service.get(SystemConfigDefinitions.ACCESS_TOKEN_TTL))
            .isEqualTo(Duration.ofMinutes(5));
    }
}
