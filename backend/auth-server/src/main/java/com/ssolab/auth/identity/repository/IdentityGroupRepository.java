package com.ssolab.auth.identity.repository;

import com.ssolab.auth.identity.model.IdentityGroupEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityGroupRepository extends JpaRepository<IdentityGroupEntity, UUID> {

    boolean existsByParentIsNullAndNormalizedName(String normalizedName);

    boolean existsByParentIdAndNormalizedName(UUID parentId, String normalizedName);

    boolean existsByParentId(UUID parentId);
}
