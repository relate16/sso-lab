package com.ssolab.auth.passwordless.totp;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssolab.auth.audit.AuditEvent;
import com.ssolab.auth.audit.AuditService;
import com.ssolab.auth.audit.AuditSource;
import com.ssolab.auth.identity.model.AccountStatus;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import com.ssolab.auth.identity.service.IdentityInputNormalizer;
import com.ssolab.auth.passwordless.crypto.TotpSecretCipher;
import com.ssolab.auth.passwordless.recovery.RecoveryCodeService;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class TotpServiceTest {

    @Test
    void recordsOneDisabledAuditAfterCredentialAndRecoveryInvalidation() {
        TotpCredentialRepository credentials = mock(TotpCredentialRepository.class);
        UserIdentityRepository users = mock(UserIdentityRepository.class);
        RecoveryCodeService recoveryCodes = mock(RecoveryCodeService.class);
        AuditService audit = mock(AuditService.class);
        UserIdentityEntity user = mock(UserIdentityEntity.class);
        TotpCredentialEntity credential = mock(TotpCredentialEntity.class);
        UUID userId = UUID.randomUUID();
        String traceId = UUID.randomUUID().toString();

        when(user.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(credentials.findLockedByUserId(userId)).thenReturn(Optional.of(credential));

        TotpService service = new TotpService(
            credentials,
            users,
            mock(IdentityInputNormalizer.class),
            mock(TotpAlgorithm.class),
            mock(TotpSecretCipher.class),
            recoveryCodes,
            audit,
            Clock.systemUTC()
        );

        service.disable(userId, traceId);

        InOrder order = inOrder(credentials, recoveryCodes, audit);
        order.verify(credentials).delete(credential);
        order.verify(credentials).flush();
        order.verify(recoveryCodes).invalidateAll(userId);
        order.verify(audit).recordWithinTransaction(
            AuditEvent.TOTP_DISABLED,
            userId,
            userId,
            true,
            AuditSource.AUTH_WEB,
            traceId
        );
        verify(audit, times(1)).recordWithinTransaction(
            AuditEvent.TOTP_DISABLED,
            userId,
            userId,
            true,
            AuditSource.AUTH_WEB,
            traceId
        );
    }
}
