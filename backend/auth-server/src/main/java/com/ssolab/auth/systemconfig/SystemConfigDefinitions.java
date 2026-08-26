package com.ssolab.auth.systemconfig;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

public final class SystemConfigDefinitions {

    public static final SystemConfigDefinition<Duration> SSO_SESSION_IDLE_TIMEOUT = duration(
        "SSO_SESSION_IDLE_TIMEOUT", Duration.ofMinutes(30), Duration.ofMinutes(1), Duration.ofHours(24)
    );
    public static final SystemConfigDefinition<Duration> SSO_SESSION_ABSOLUTE_TIMEOUT = duration(
        "SSO_SESSION_ABSOLUTE_TIMEOUT", Duration.ofHours(8), Duration.ofMinutes(5), Duration.ofDays(7)
    );
    public static final SystemConfigDefinition<Duration> ACCESS_TOKEN_TTL = duration(
        "ACCESS_TOKEN_TTL", Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofHours(1)
    );
    public static final SystemConfigDefinition<Duration> ID_TOKEN_TTL = duration(
        "ID_TOKEN_TTL", Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofHours(1)
    );
    public static final SystemConfigDefinition<Duration> REFRESH_TOKEN_TTL = duration(
        "REFRESH_TOKEN_TTL", Duration.ofHours(8), Duration.ofMinutes(5), Duration.ofDays(30)
    );
    public static final SystemConfigDefinition<Duration> ADMIN_REAUTH_TTL = duration(
        "ADMIN_REAUTH_TTL", Duration.ofMinutes(5), Duration.ofSeconds(30), Duration.ofMinutes(30)
    );
    public static final SystemConfigDefinition<Duration> EMAIL_OTP_TTL = duration(
        "EMAIL_OTP_TTL", Duration.ofMinutes(5), Duration.ofSeconds(30), Duration.ofMinutes(30)
    );
    public static final SystemConfigDefinition<Integer> EMAIL_OTP_MAX_ATTEMPTS = integer(
        "EMAIL_OTP_MAX_ATTEMPTS", 5, 1, 20
    );
    public static final SystemConfigDefinition<Duration> EMAIL_OTP_RESEND_INTERVAL = duration(
        "EMAIL_OTP_RESEND_INTERVAL", Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofHours(1)
    );

    public static final Set<SystemConfigDefinition<?>> ALL = Set.of(
        SSO_SESSION_IDLE_TIMEOUT,
        SSO_SESSION_ABSOLUTE_TIMEOUT,
        ACCESS_TOKEN_TTL,
        ID_TOKEN_TTL,
        REFRESH_TOKEN_TTL,
        ADMIN_REAUTH_TTL,
        EMAIL_OTP_TTL,
        EMAIL_OTP_MAX_ATTEMPTS,
        EMAIL_OTP_RESEND_INTERVAL
    );

    private SystemConfigDefinitions() {
    }

    private static SystemConfigDefinition<Duration> duration(
        String key,
        Duration defaultValue,
        Duration minimum,
        Duration maximum
    ) {
        return new SystemConfigDefinition<>(
            key,
            defaultValue,
            SystemConfigDefinitions::parseDuration,
            SystemConfigDefinitions::formatDuration,
            value -> !value.isNegative() && !value.isZero()
                && value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0
        );
    }

    private static SystemConfigDefinition<Integer> integer(
        String key,
        int defaultValue,
        int minimum,
        int maximum
    ) {
        return new SystemConfigDefinition<>(
            key,
            defaultValue,
            Integer::valueOf,
            String::valueOf,
            value -> value >= minimum && value <= maximum
        );
    }

    static Duration parseDuration(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("duration must not be blank");
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("p")) {
            return Duration.parse(normalized.toUpperCase(Locale.ROOT));
        }
        if (!normalized.matches("[0-9]+[smhd]")) {
            throw new IllegalArgumentException("duration must use s, m, h, d, or ISO-8601 format");
        }
        long amount = Long.parseLong(normalized.substring(0, normalized.length() - 1));
        return switch (normalized.charAt(normalized.length() - 1)) {
            case 's' -> Duration.ofSeconds(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("unsupported duration unit");
        };
    }

    static String formatDuration(Duration value) {
        long seconds = value.toSeconds();
        if (seconds % 86_400 == 0) {
            return (seconds / 86_400) + "d";
        }
        if (seconds % 3_600 == 0) {
            return (seconds / 3_600) + "h";
        }
        if (seconds % 60 == 0) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }
}
