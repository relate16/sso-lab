package com.ssolab.auth.passwordless.recovery;

import com.ssolab.auth.passwordless.totp.TotpEnrollmentStart;
import java.util.UUID;

public record TotpRecoveryResult(boolean success, UUID userId, TotpEnrollmentStart enrollment) {

    public static TotpRecoveryResult failed() {
        return new TotpRecoveryResult(false, null, null);
    }
}
