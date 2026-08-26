package com.ssolab.shared.secret;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SecretFileValue {
    private static final long MAX_SECRET_BYTES = 64 * 1024;

    private SecretFileValue() {
    }

    public static String resolve(String inlineValue, String fileName, String logicalName) {
        boolean hasInline = inlineValue != null && !inlineValue.isBlank();
        boolean hasFile = fileName != null && !fileName.isBlank();
        if (hasInline == hasFile) {
            throw new IllegalArgumentException(
                logicalName + " must be supplied by exactly one source"
            );
        }
        if (hasInline) {
            return inlineValue;
        }

        Path path = Path.of(fileName).toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_SECRET_BYTES) {
                throw new IllegalArgumentException(logicalName + " secret file is invalid");
            }
            String value = Files.readString(path, StandardCharsets.UTF_8).strip();
            if (value.isEmpty()) {
                throw new IllegalArgumentException(logicalName + " secret file is empty");
            }
            return value;
        } catch (IOException exception) {
            throw new IllegalArgumentException(logicalName + " secret file is unavailable");
        }
    }
}
