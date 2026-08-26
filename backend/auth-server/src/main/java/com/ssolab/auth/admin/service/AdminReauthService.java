package com.ssolab.auth.admin.service;

import com.ssolab.auth.admin.audit.AdminAuditEvent;
import com.ssolab.auth.admin.audit.AdminAuditService;
import com.ssolab.auth.admin.audit.AdminAuditSource;
import com.ssolab.auth.admin.reauth.AdminReauthMethod;
import com.ssolab.auth.admin.reauth.AdminReauthProof;
import com.ssolab.auth.admin.reauth.AdminReauthProofService;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import com.ssolab.auth.identity.service.IdentityNotFoundException;
import com.ssolab.auth.identity.service.UserIdentityService;
import com.ssolab.auth.passwordless.otp.LoginVerificationResult;
import com.ssolab.auth.passwordless.otp.OtpRequestResult;
import com.ssolab.auth.passwordless.otp.OtpVerificationStatus;
import com.ssolab.auth.passwordless.otp.PasswordlessEmailOtpService;
import com.ssolab.auth.passwordless.totp.TotpService;
import com.ssolab.auth.passwordless.totp.TotpVerificationResult;
import com.ssolab.auth.passwordless.totp.TotpVerificationStatus;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminReauthService {

    private final AdminIdentityGuard guard;
    private final PasswordlessEmailOtpService emailOtpService;
    private final TotpService totpService;
    private final AdminReauthProofService proofService;
    private final UserIdentityRepository userRepository;
    private final UserIdentityService identityService;
    private final AdminAuditService auditService;

    public AdminReauthService(
        AdminIdentityGuard guard,
        PasswordlessEmailOtpService emailOtpService,
        TotpService totpService,
        AdminReauthProofService proofService,
        UserIdentityRepository userRepository,
        UserIdentityService identityService,
        AdminAuditService auditService
    ) {
        this.guard = guard;
        this.emailOtpService = emailOtpService;
        this.totpService = totpService;
        this.proofService = proofService;
        this.userRepository = userRepository;
        this.identityService = identityService;
        this.auditService = auditService;
    }

    @Transactional
    public AdminDtos.ReauthStartResponse startEmail(UUID actorId) {
        guard.requireActiveAdmin(actorId);
        OtpRequestResult result = emailOtpService.sendAdminReauthOtp(actorId);
        if (result.status() != OtpVerificationStatus.SUCCESS) {
            throw new AdminReauthException("reauth_resend_not_available");
        }
        return new AdminDtos.ReauthStartResponse(
            result.challengeId(), totpService.isEnrolled(actorId)
        );
    }

    @Transactional
    public AdminDtos.ReauthProofResponse verify(
        UUID actorId,
        AdminDtos.ReauthVerifyRequest request,
        String traceId
    ) {
        guard.requireActiveAdmin(actorId);
        AdminReauthMethod method;
        try {
            method = AdminReauthMethod.valueOf(request.method());
        } catch (IllegalArgumentException exception) {
            failed(actorId, traceId);
            throw new AdminReauthException("invalid_reauth_method");
        }

        char[] candidate = request.code().toCharArray();
        boolean verified;
        try {
            verified = switch (method) {
                case EMAIL_OTP -> verifyEmail(actorId, request.challengeId(), candidate);
                case TOTP -> verifyTotp(actorId, candidate);
            };
        } finally {
            Arrays.fill(candidate, '\0');
        }
        if (!verified) {
            failed(actorId, traceId);
            throw new AdminReauthException("invalid_reauth_code");
        }
        AdminReauthProof proof = proofService.issue(actorId, method);
        auditService.record(
            AdminAuditEvent.ADMIN_REAUTH_SUCCESS,
            actorId, actorId, true, AdminAuditSource.ADMIN_WEB, traceId
        );
        return new AdminDtos.ReauthProofResponse(proof.value(), proof.expiresAt());
    }

    @Transactional
    public AdminDtos.EmailRevealResponse revealEmail(
        UUID actorId,
        UUID targetId,
        String proof,
        String traceId
    ) {
        guard.requireActiveAdmin(actorId);
        if (!proofService.validate(actorId, proof)) {
            throw new AdminReauthException("reauthentication_required");
        }
        UserIdentityEntity target = userRepository.findById(targetId).orElseThrow(
            () -> new IdentityNotFoundException("user was not found")
        );
        String email = identityService.decryptEmail(target);
        auditService.record(
            AdminAuditEvent.ADMIN_EMAIL_REVEALED,
            actorId, targetId, true, AdminAuditSource.ADMIN_WEB, traceId
        );
        return new AdminDtos.EmailRevealResponse(email);
    }

    private boolean verifyEmail(UUID actorId, UUID challengeId, char[] candidate) {
        if (challengeId == null) {
            return false;
        }
        LoginVerificationResult result = emailOtpService.verifyAdminReauth(
            actorId, challengeId, candidate
        );
        return result.status() == OtpVerificationStatus.SUCCESS;
    }

    private boolean verifyTotp(UUID actorId, char[] candidate) {
        TotpVerificationResult result = totpService.verifyAdminReauth(actorId, candidate);
        return result.status() == TotpVerificationStatus.SUCCESS;
    }

    private void failed(UUID actorId, String traceId) {
        auditService.record(
            AdminAuditEvent.ADMIN_REAUTH_FAILED,
            actorId, actorId, false, AdminAuditSource.ADMIN_WEB, traceId
        );
    }
}
