package com.ssolab.admin.session;

import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ElevatedAdminSessionService {

    private static final String PROOF_ATTRIBUTE =
        ElevatedAdminSessionService.class.getName() + ".proof";
    private final Clock clock;

    public ElevatedAdminSessionService() {
        this(Clock.systemUTC());
    }

    ElevatedAdminSessionService(Clock clock) {
        this.clock = clock;
    }

    public void elevate(
        HttpSession session,
        UUID actorId,
        String proof,
        Instant expiresAt
    ) {
        session.setAttribute(PROOF_ATTRIBUTE, new ElevatedProof(actorId, proof, expiresAt));
    }

    public ElevatedStatus status(HttpSession session, UUID actorId) {
        ElevatedProof proof = validProof(session, actorId);
        return proof == null
            ? new ElevatedStatus(false, null)
            : new ElevatedStatus(true, proof.expiresAt());
    }

    public String requireProof(HttpSession session, UUID actorId) {
        ElevatedProof proof = validProof(session, actorId);
        if (proof == null) {
            throw new IllegalStateException("administrator re-authentication is required");
        }
        return proof.value();
    }

    private ElevatedProof validProof(HttpSession session, UUID actorId) {
        Object value = session.getAttribute(PROOF_ATTRIBUTE);
        if (!(value instanceof ElevatedProof proof)
            || !proof.actorId().equals(actorId)
            || !clock.instant().isBefore(proof.expiresAt())) {
            session.removeAttribute(PROOF_ATTRIBUTE);
            return null;
        }
        return proof;
    }

    private record ElevatedProof(UUID actorId, String value, Instant expiresAt) {
        @Override public String toString() {
            return "ElevatedProof[actorId=" + actorId + ", value=<redacted>, expiresAt="
                + expiresAt + "]";
        }
    }

    public record ElevatedStatus(boolean elevated, Instant expiresAt) {
    }
}
