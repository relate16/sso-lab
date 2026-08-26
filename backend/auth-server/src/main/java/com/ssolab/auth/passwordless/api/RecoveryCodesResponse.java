package com.ssolab.auth.passwordless.api;

import com.ssolab.auth.passwordless.recovery.RecoveryCodeBatch;
import java.util.List;

public record RecoveryCodesResponse(List<String> recoveryCodes) {

    public RecoveryCodesResponse {
        recoveryCodes = List.copyOf(recoveryCodes);
    }

    public static RecoveryCodesResponse from(RecoveryCodeBatch batch) {
        return new RecoveryCodesResponse(batch.codes());
    }

    @Override
    public String toString() {
        return "RecoveryCodesResponse[recoveryCodes=REDACTED, count="
            + recoveryCodes.size() + "]";
    }
}
