package com.ssolab.auth.admin.reauth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminReauthProofRepository
    extends JpaRepository<AdminReauthProofEntity, UUID> {

    Optional<AdminReauthProofEntity> findByProofHash(byte[] proofHash);
}
