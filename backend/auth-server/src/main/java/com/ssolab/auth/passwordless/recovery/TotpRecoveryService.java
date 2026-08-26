package com.ssolab.auth.passwordless.recovery;

import com.ssolab.auth.identity.model.AccountStatus;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import com.ssolab.auth.identity.service.IdentityInputNormalizer;
import com.ssolab.auth.passwordless.totp.TotpEnrollmentStart;
import com.ssolab.auth.passwordless.totp.TotpService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TotpRecoveryService {

    private final UserIdentityRepository userRepository;
    private final IdentityInputNormalizer inputNormalizer;
    private final RecoveryCodeService recoveryCodeService;
    private final TotpService totpService;

    public TotpRecoveryService(
        UserIdentityRepository userRepository,
        IdentityInputNormalizer inputNormalizer,
        RecoveryCodeService recoveryCodeService,
        TotpService totpService
    ) {
        this.userRepository = userRepository;
        this.inputNormalizer = inputNormalizer;
        this.recoveryCodeService = recoveryCodeService;
        this.totpService = totpService;
    }

    @Transactional
    public TotpRecoveryResult start(String userId, String recoveryCode) {
        String normalizedUserId;
        try {
            normalizedUserId = inputNormalizer.normalizeUserId(userId);
        } catch (IllegalArgumentException exception) {
            return TotpRecoveryResult.failed();
        }
        UserIdentityEntity user = userRepository.findByNormalizedUserId(normalizedUserId)
            .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
            .orElse(null);
        if (user == null || !recoveryCodeService.consume(user.getId(), recoveryCode)) {
            return TotpRecoveryResult.failed();
        }
        totpService.disableForRecovery(user.getId());
        TotpEnrollmentStart enrollment = totpService.startEnrollment(user.getId());
        return new TotpRecoveryResult(true, user.getId(), enrollment);
    }
}
