package com.ssolab.gallery.inquiry;

import com.ssolab.gallery.api.GalleryValidationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InquiryRateLimiter {
    private static final int LIMIT = 5;
    private static final Duration WINDOW = Duration.ofHours(1);
    private final ConcurrentHashMap<String, ArrayDeque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();
    public void check(String identifier) {
        Instant cutoff = clock.instant().minus(WINDOW);
        ArrayDeque<Instant> times = attempts.computeIfAbsent(identifier, ignored -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) times.removeFirst();
            if (times.size() >= LIMIT) throw new GalleryValidationException("inquiry rate limit exceeded");
            times.addLast(clock.instant());
        }
    }
}
