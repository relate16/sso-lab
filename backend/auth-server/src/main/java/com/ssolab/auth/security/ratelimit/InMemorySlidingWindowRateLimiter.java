package com.ssolab.auth.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemorySlidingWindowRateLimiter {
    private static final int MAX_BUCKETS = 10_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemorySlidingWindowRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean tryAcquire(String key, int limit, Duration window) {
        if (limit <= 0) {
            return true;
        }
        Instant now = clock.instant();
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= MAX_BUCKETS) {
                removeExpired(now);
                if (buckets.size() >= MAX_BUCKETS) {
                    return false;
                }
            }
            bucket = buckets.computeIfAbsent(key, ignored -> new Bucket());
        }
        return bucket.tryAcquire(now, window, limit);
    }

    private void removeExpired(Instant now) {
        buckets.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private static final class Bucket {
        private final ArrayDeque<Instant> requests = new ArrayDeque<>();
        private Instant lastSeen = Instant.EPOCH;

        synchronized boolean tryAcquire(Instant now, Duration window, int limit) {
            Instant threshold = now.minus(window);
            while (!requests.isEmpty() && !requests.peekFirst().isAfter(threshold)) {
                requests.removeFirst();
            }
            lastSeen = now;
            if (requests.size() >= limit) {
                return false;
            }
            requests.addLast(now);
            return true;
        }

        synchronized boolean isExpired(Instant now) {
            return requests.isEmpty() || lastSeen.isBefore(now.minus(Duration.ofHours(1)));
        }
    }
}
