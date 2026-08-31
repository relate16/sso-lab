package com.ssolab.auth.passwordless.otp;

import com.ssolab.auth.identity.service.CreateUserIdentityCommand;
import com.ssolab.auth.passwordless.mail.OtpMailMessage;
import com.ssolab.auth.passwordless.mail.VerificationMailSender;
import com.ssolab.auth.passwordless.emailchange.EmailChangeVerificationResult;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;

@Service
@Validated
public class PasswordlessEmailOtpService {

    private final EmailOtpTransactionService transactionService;
    private final VerificationMailSender mailSender;

    public PasswordlessEmailOtpService(
        EmailOtpTransactionService transactionService,
        VerificationMailSender mailSender
    ) {
        this.transactionService = transactionService;
        this.mailSender = mailSender;
    }

    public OtpRequestResult startSignup(@Valid CreateUserIdentityCommand command) {
        return dispatch(transactionService.issueSignup(command));
    }

    public OtpRequestResult sendLoginOtp(String userId) {
        return dispatch(transactionService.issueLogin(userId));
    }

    public OtpRequestResult resendSignup(UUID challengeId) {
        return resend(challengeId, OtpPurpose.SIGNUP);
    }

    public OtpRequestResult resendLogin(UUID challengeId) {
        return resend(challengeId, OtpPurpose.LOGIN);
    }

    public OtpRequestResult startEmailChange(UUID userId, String newEmail) {
        return dispatch(transactionService.issueEmailChange(userId, newEmail));
    }

    public OtpRequestResult resendEmailChange(UUID userId, UUID challengeId) {
        OtpDelivery delivery = transactionService.resendEmailChange(userId, challengeId);
        if (delivery == null) {
            return OtpRequestResult.failed(OtpVerificationStatus.RESEND_TOO_SOON, challengeId);
        }
        return dispatch(delivery);
    }

    public EmailChangeVerificationResult verifyEmailChange(
        UUID userId,
        UUID challengeId,
        char[] candidate,
        String traceId
    ) {
        return transactionService.verifyEmailChange(userId, challengeId, candidate, traceId);
    }

    public SignupVerificationResult verifySignup(UUID challengeId, char[] candidate) {
        return transactionService.verifySignup(challengeId, candidate);
    }

    public LoginVerificationResult verifyLogin(UUID challengeId, char[] candidate) {
        return transactionService.verifyLogin(challengeId, candidate);
    }

    public OtpRequestResult sendAdminReauthOtp(UUID actorId) {
        OtpDelivery delivery = transactionService.issueAdminReauth(actorId);
        if (delivery == null) {
            return OtpRequestResult.failed(OtpVerificationStatus.RESEND_TOO_SOON, null);
        }
        return dispatch(delivery);
    }

    public LoginVerificationResult verifyAdminReauth(
        UUID actorId,
        UUID challengeId,
        char[] candidate
    ) {
        return transactionService.verifyAdminReauth(actorId, challengeId, candidate);
    }

    private OtpRequestResult resend(UUID challengeId, OtpPurpose purpose) {
        OtpDelivery delivery = transactionService.resend(challengeId, purpose);
        if (delivery == null) {
            return OtpRequestResult.failed(OtpVerificationStatus.RESEND_TOO_SOON, challengeId);
        }
        return dispatch(delivery);
    }

    private OtpRequestResult dispatch(OtpDelivery delivery) {
        try (delivery) {
            delivery.recipientIfDeliverable().ifPresent(recipient -> mailSender.sendOtp(
                new OtpMailMessage(
                    delivery.challengeId(),
                    delivery.purpose(),
                    recipient,
                    delivery.code(),
                    delivery.expiresAt()
                )
            ));
            return OtpRequestResult.sent(delivery);
        }
    }
}
