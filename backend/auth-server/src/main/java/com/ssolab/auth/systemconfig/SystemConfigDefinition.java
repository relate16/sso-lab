package com.ssolab.auth.systemconfig;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public record SystemConfigDefinition<T>(
    String key,
    T defaultValue,
    Function<String, T> parser,
    Function<T, String> formatter,
    Predicate<T> validator
) {

    public SystemConfigDefinition {
        if (key == null || !key.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("system config key has an invalid format");
        }
        Objects.requireNonNull(defaultValue);
        Objects.requireNonNull(parser);
        Objects.requireNonNull(formatter);
        Objects.requireNonNull(validator);
        if (!validator.test(defaultValue)) {
            throw new IllegalArgumentException("system config default value is invalid: " + key);
        }
    }

    public T parse(String rawValue) {
        T parsed = parser.apply(rawValue);
        if (!validator.test(parsed)) {
            throw new IllegalArgumentException("system config value is outside the safe range: " + key);
        }
        return parsed;
    }

    public String format(T value) {
        if (!validator.test(value)) {
            throw new IllegalArgumentException("system config value is outside the safe range: " + key);
        }
        return formatter.apply(value);
    }
}
