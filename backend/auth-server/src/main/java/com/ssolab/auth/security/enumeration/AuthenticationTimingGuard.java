package com.ssolab.auth.security.enumeration;

import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationTimingGuard {
    private final long minimumNanos;

    public AuthenticationTimingGuard(EnumerationDefenseProperties properties) {
        this.minimumNanos = properties.minimumResponseTime().toNanos();
    }

    public <T> T protect(Supplier<T> action) {
        long started = System.nanoTime();
        try {
            return action.get();
        } finally {
            long remaining = minimumNanos - (System.nanoTime() - started);
            while (remaining > 0) {
                LockSupport.parkNanos(remaining);
                remaining = minimumNanos - (System.nanoTime() - started);
            }
        }
    }
}
