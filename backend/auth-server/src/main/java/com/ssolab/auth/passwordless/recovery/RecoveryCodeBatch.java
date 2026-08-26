package com.ssolab.auth.passwordless.recovery;

import java.util.List;

public record RecoveryCodeBatch(List<String> codes) {

    public RecoveryCodeBatch {
        codes = List.copyOf(codes);
        if (codes.size() != 10) {
            throw new IllegalArgumentException("exactly 10 recovery codes are required");
        }
    }

    @Override
    public String toString() {
        return "RecoveryCodeBatch[codes=REDACTED, count=" + codes.size() + "]";
    }
}
