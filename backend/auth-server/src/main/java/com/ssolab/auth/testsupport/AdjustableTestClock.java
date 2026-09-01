package com.ssolab.auth.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
@ConditionalOnProperty(prefix = "sso.test-support", name = "enabled", havingValue = "true")
public final class AdjustableTestClock extends Clock {

    private final AtomicReference<Instant> current = new AtomicReference<>(Instant.now());

    public void set(Instant instant) {
        current.set(Objects.requireNonNull(instant));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (!ZoneOffset.UTC.equals(zone)) {
            throw new IllegalArgumentException("test clock only supports UTC");
        }
        return this;
    }

    @Override
    public Instant instant() {
        return current.get();
    }
}
